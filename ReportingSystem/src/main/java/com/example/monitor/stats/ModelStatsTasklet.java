package com.example.monitor.stats;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Step 4 of the job: calculates model_stats from unit_stats and replaces the table in one transaction.
 * The table is tiny (one row per model), so it is always calculated completely.
 */
public class ModelStatsTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(ModelStatsTasklet.class);

    private final StatsRepository stats;
    private final int windowDays;

    public ModelStatsTasklet(StatsRepository stats, int windowDays) {
        this.stats = stats;
        this.windowDays = windowDays;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDateTime runAt = chunkContext.getStepContext().getStepExecution()
                .getJobParameters().getLocalDateTime(StatsJobConfig.RUN_AT);
        refresh(runAt);
        return RepeatStatus.FINISHED;
    }

    void refresh(LocalDateTime runAt) {
        List<ModelStats> rows = ModelStatsCalculator.calculate(stats.loadModels(), stats.loadUnitRates(), windowDays, runAt);
        stats.replaceModelStats(rows);
        log.info("model_stats: {} models", rows.size());
    }
}
