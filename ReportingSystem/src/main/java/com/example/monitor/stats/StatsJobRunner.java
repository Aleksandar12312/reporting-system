package com.example.monitor.stats;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts the statistics job.
 * <ul>
 *   <li>once when the application is ready: after the mock seeder has finished, so a fresh database is
 *       filled completely (the tables are empty, so step 1 builds everything from the start)</li>
 *   <li>then on the schedule stats.refresh-cron (default: 30 seconds after every 5-minute mock tick):
 *       only the events added since the last run are read</li>
 * </ul>
 * Before the first run the stats tables are created if they are missing (db/schema_stats.sql).
 * Only one run at a time: a run that is due while another one is still busy is skipped.
 */
@Component
@ConditionalOnProperty(prefix = "stats", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StatsJobRunner {

    private static final Logger log = LoggerFactory.getLogger(StatsJobRunner.class);

    private final JobLauncher jobLauncher;
    private final Job statsRefreshJob;
    private final StatsRepository stats;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();
    private final AtomicBoolean ready = new AtomicBoolean();
    private volatile boolean tablesChecked;

    public StatsJobRunner(JobLauncher jobLauncher, Job statsRefreshJob, StatsRepository stats, Clock clock) {
        this.jobLauncher = jobLauncher;
        this.statsRefreshJob = statsRefreshJob;
        this.stats = stats;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        ready.set(true);
        run();
    }

    @Scheduled(cron = "${stats.refresh-cron:30 */5 * * * *}", zone = "UTC")
    public void onSchedule() {
        // The schedule starts before the application is ready (the seeder may still be writing)
        if (ready.get()) {
            run();
        }
    }

    /** Runs the job now, unless it is already running. Returns the result, or null when it was skipped. */
    public BatchStatus run() {
        if (!running.tryLock()) {
            log.info("Statistics job is still running, this run is skipped");
            return null;
        }
        try {
            // Once per application start; tried again on the next run if the database was not reachable
            if (!tablesChecked) {
                stats.createTablesIfMissing();
                tablesChecked = true;
            }
            JobParameters params = new JobParametersBuilder()
                    .addLocalDateTime(StatsJobConfig.RUN_AT, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                    .toJobParameters();
            JobExecution execution = jobLauncher.run(statsRefreshJob, params);
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                log.warn("Statistics job ended with {}: {}", execution.getStatus(), execution.getAllFailureExceptions());
            }
            return execution.getStatus();
        } catch (Exception e) {
            // Logged, not thrown: the application keeps running and the next scheduled run tries again
            log.error("Statistics job could not be started", e);
            return BatchStatus.FAILED;
        } finally {
            running.unlock();
        }
    }
}
