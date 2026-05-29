package com.cloudvault.storage_engine.scheduler;

import com.cloudvault.storage_engine.service.LifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LifecycleScheduler {

    private final LifecycleService lifecycleService;


    @Scheduled(cron = "${lifecycle.cron.daily}")
    public void runDailyLifecycleCheck() {
        log.info("Daily lifecycle check triggered");
        lifecycleService.checkAndTransitionBuckets();
    }


    @Scheduled(cron = "${lifecycle.cron.hourly}")
    public void runRestoreProcessor() {
        lifecycleService.processRestoreRequests();
    }

    @Scheduled(cron = "${lifecycle.cron.hourly}")
    public void runRestoreExpiryCheck() {
        lifecycleService.checkExpiredRestores();
    }
}