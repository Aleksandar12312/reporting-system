package com.example.monitor.stats;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/** Step 3 of the job (writer): inserts or replaces the unit_stats rows of one chunk. */
public class UnitStatsWriter implements ItemWriter<UnitStats> {

    private final StatsRepository stats;

    public UnitStatsWriter(StatsRepository stats) {
        this.stats = stats;
    }

    @Override
    public void write(Chunk<? extends UnitStats> chunk) {
        if (!chunk.isEmpty()) {
            stats.saveUnitStats(chunk.getItems());
        }
    }
}
