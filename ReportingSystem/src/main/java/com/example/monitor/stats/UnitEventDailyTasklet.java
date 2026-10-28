package com.example.monitor.stats;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Step 2 of the job: adds the new events to unit_event_daily (per unit, day and event name).
 *
 * The new events are the ids after the state's last_event_id, up to the id fixed by step 1. They are read in
 * slices of {@code stats.slice-size} ids. Spring Batch calls this tasklet once per slice, each call in its own
 * transaction, and the slice and the new last_event_id are committed together. So a crash in the middle never
 * counts an event twice or loses one: the next run continues after the last committed slice.
 * On the first run (or after a rebuild) the slices simply start at id 0, so the full load is the same code.
 */
public class UnitEventDailyTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(UnitEventDailyTasklet.class);

    private final StatsRepository stats;
    private final long sliceSize;

    public UnitEventDailyTasklet(StatsRepository stats, long sliceSize) {
        this.stats = stats;
        this.sliceSize = sliceSize;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long toEventId = chunkContext.getStepContext().getStepExecution().getJobExecution()
                .getExecutionContext().getLong(PrepareStatsTasklet.TO_EVENT_ID);
        LocalDateTime runAt = chunkContext.getStepContext().getStepExecution()
                .getJobParameters().getLocalDateTime(StatsJobConfig.RUN_AT);
        return processNextSlice(toEventId, runAt);
    }

    /** Adds one slice; FINISHED when everything up to {@code toEventId} is counted. */
    RepeatStatus processNextSlice(long toEventId, LocalDateTime runAt) {
        long from = stats.loadState().map(EtlState::lastEventId).orElse(0L);
        if (from >= toEventId) {
            return RepeatStatus.FINISHED;
        }
        long to = Math.min(toEventId, from + sliceSize);
        // The slice and the new state are committed together, so an event is never counted twice
        int rows = stats.addEventsToDaily(from, to);
        stats.saveState(to, runAt);
        log.info("unit_event_daily: events {}..{} added ({} rows written)", from + 1, to, rows);
        return to >= toEventId ? RepeatStatus.FINISHED : RepeatStatus.CONTINUABLE;
    }
}
