package com.cloudvault.storage_engine.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class PartitionMaintenanceScheduler {

    private final JdbcTemplate jdbcTemplate;


    @Scheduled(cron = "0 0 2 1 1 ?")
    public void addYearlyPartitions() {
        int nextYear = LocalDate.now().getYear() + 1;
        log.info("Adding partitions for year {}", nextYear);

        StringBuilder sql = new StringBuilder(
                "ALTER TABLE usage_records REORGANIZE PARTITION p_future INTO (");

        for (int month = 1; month <= 12; month++) {
            int partitionValue = nextYear * 100 + month + 1;
            if (month == 12) {
                partitionValue = (nextYear + 1) * 100 + 1;
            }
            sql.append(String.format(
                    "PARTITION p%d_%02d VALUES LESS THAN (%d),",
                    nextYear, month, partitionValue));
        }

        sql.append("PARTITION p_future VALUES LESS THAN MAXVALUE)");

        try {
            jdbcTemplate.execute(sql.toString());
            log.info("Partitions added successfully for year {}", nextYear);
        } catch (Exception e) {
            log.error("Failed to add partitions for year {}: {}",
                    nextYear, e.getMessage());
        }
    }
}