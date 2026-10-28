package com.example.monitor.stats;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The Spring Batch job that keeps the statistics tables up to date.
 *
 * <pre>
 * statsRefreshJob
 *   1. prepareStatsStep    (tasklet)  rebuild from the start if needed; fix the last event id of this run
 *   2. unitEventDailyStep  (tasklet)  add the new events to unit_event_daily, slice by slice
 *   3. unitStatsStep       (chunk)    reader: every unit with its faults, processor: window and rate,
 *                                     writer: upsert into unit_stats (a new unit gets its row here)
 *   4. modelStatsStep      (tasklet)  median and mean per model into model_stats
 * </pre>
 *
 * Spring Boot provides the JobRepository, the JobLauncher and the BATCH_ metadata tables.
 * The job is started by {@link StatsJobRunner}, never automatically by Spring Boot
 * (spring.batch.job.enabled=false in application.yml).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(StatsProperties.class)
@ConditionalOnProperty(prefix = "stats", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StatsJobConfig {

    public static final String JOB_NAME = "statsRefreshJob";

    /** Job parameter: the time of the run (UTC). It also makes every run a new job instance. */
    public static final String RUN_AT = "runAt";

    /** System clock (replaceable in tests). */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Job statsRefreshJob(JobRepository jobRepository, Step prepareStatsStep, Step unitEventDailyStep,
                               Step unitStatsStep, Step modelStatsStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(prepareStatsStep)
                .next(unitEventDailyStep)
                .next(unitStatsStep)
                .next(modelStatsStep)
                .build();
    }

    @Bean
    public Step prepareStatsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                 StatsRepository stats) {
        return new StepBuilder("prepareStatsStep", jobRepository)
                .tasklet(new PrepareStatsTasklet(stats), transactionManager)
                .build();
    }

    @Bean
    public Step unitEventDailyStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                   StatsRepository stats, StatsProperties props) {
        return new StepBuilder("unitEventDailyStep", jobRepository)
                .tasklet(new UnitEventDailyTasklet(stats, props.sliceSize()), transactionManager)
                .build();
    }

    @Bean
    public Step unitStatsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              JdbcCursorItemReader<UnitFaultTotals> unitFaultTotalsReader,
                              UnitStatsProcessor unitStatsProcessor, StatsRepository stats, StatsProperties props) {
        return new StepBuilder("unitStatsStep", jobRepository)
                .<UnitFaultTotals, UnitStats>chunk(props.chunkSize(), transactionManager)
                .reader(unitFaultTotalsReader)
                .processor(unitStatsProcessor)
                .writer(new UnitStatsWriter(stats))
                .build();
    }

    @Bean
    public Step modelStatsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                               StatsRepository stats, StatsProperties props) {
        return new StepBuilder("modelStatsStep", jobRepository)
                .tasklet(new ModelStatsTasklet(stats, props.windowDays()), transactionManager)
                .build();
    }

    /**
     * Created anew for every run (step scope), because the window depends on the run time.
     * The return type is the concrete reader on purpose: Spring Batch must see that it has to be opened and closed.
     */
    @Bean
    @StepScope
    public JdbcCursorItemReader<UnitFaultTotals> unitFaultTotalsReader(
            DataSource dataSource, StatsProperties props, StatsRepository stats,
            @Value("#{jobParameters['runAt']}") LocalDateTime runAt) {
        return new JdbcCursorItemReaderBuilder<UnitFaultTotals>()
                .name("unitFaultTotalsReader")
                .dataSource(dataSource)
                .sql(stats.unitTotalsSql())
                .queryArguments(stats.unitTotalsArgs(runAt.minusDays(props.windowDays()), runAt.toLocalDate()))
                .rowMapper((rs, rowNum) -> new UnitFaultTotals(
                        rs.getLong("unit_id"),
                        rs.getLong("unit_type_id"),
                        rs.getObject("creation_date", LocalDateTime.class),
                        rs.getInt("faults_in_window"),
                        rs.getInt("total_faults"),
                        rs.getObject("last_fault_date", LocalDate.class)))
                .build();
    }

    @Bean
    @StepScope
    public UnitStatsProcessor unitStatsProcessor(StatsProperties props,
                                                 @Value("#{jobParameters['runAt']}") LocalDateTime runAt) {
        return new UnitStatsProcessor(runAt, props.windowDays(), props.baselineMinDays());
    }
}
