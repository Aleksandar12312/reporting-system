package com.example.monitor.stats;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Step 1 of the job. Decides whether the statistics can be updated or must be built again from the start,
 * and fixes the last event id this run will read (events written while the job runs wait for the next run).
 *
 * The statistics are built again (all statistics tables emptied, every event read again) when:
 * <ul>
 *   <li>there is no state row yet: the first run, or the stats tables were emptied by hand</li>
 *   <li>the state points past the newest event, or its check event is gone or different: the event table
 *       was re-created (re-seed)</li>
 *   <li>unit_event_daily is empty although events were already counted: the table was dropped or emptied
 *       by hand</li>
 * </ul>
 */
public class PrepareStatsTasklet implements Tasklet {

    /** Key in the job's execution context: the highest event id this run reads. */
    public static final String TO_EVENT_ID = "toEventId";

    private static final Logger log = LoggerFactory.getLogger(PrepareStatsTasklet.class);

    private final StatsRepository stats;

    public PrepareStatsTasklet(StatsRepository stats) {
        this.stats = stats;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDateTime runAt = chunkContext.getStepContext().getStepExecution()
                .getJobParameters().getLocalDateTime(StatsJobConfig.RUN_AT);
        long toEventId = prepare(runAt);
        chunkContext.getStepContext().getStepExecution().getJobExecution()
                .getExecutionContext().putLong(TO_EVENT_ID, toEventId);
        return RepeatStatus.FINISHED;
    }

    /** Does the work of the step; returns the highest event id this run reads. */
    long prepare(LocalDateTime runAt) {
        long maxEventId = stats.maxEventId();
        Optional<String> reason = rebuildReason(stats.loadState(), maxEventId);
        if (reason.isPresent()) {
            log.info("Statistics are built from the start: {}", reason.get());
            stats.deleteAllStats();
            stats.saveState(0, runAt);
        }
        int removed = stats.deleteStatsOfRemovedUnits();
        if (removed > 0) {
            log.info("Removed unit_stats of {} units that no longer exist", removed);
        }
        return maxEventId;
    }

    /** Why the statistics must be built again; empty when they can simply be updated. */
    Optional<String> rebuildReason(Optional<EtlState> loaded, long maxEventId) {
        if (loaded.isEmpty()) {
            return Optional.of("no state yet (first run or the stats tables were emptied)");
        }
        EtlState state = loaded.get();
        if (state.lastEventId() > maxEventId) {
            return Optional.of("event table has fewer events than already counted (re-created)");
        }
        if (state.hasCheckEvent() && !stats.checkEventIsUnchanged(state)) {
            return Optional.of("event " + state.checkEventId() + " changed (event table re-created)");
        }
        if (state.lastEventId() > 0 && stats.dailyTableIsEmpty() && stats.eventsExistUpTo(state.lastEventId())) {
            return Optional.of("unit_event_daily is empty although events were already counted");
        }
        return Optional.empty();
    }
}
