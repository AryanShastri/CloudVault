package com.cloudvault.storage_engine.scheduler;

import com.cloudvault.storage_engine.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillingScheduler {

    private final BillingService billingService;

    @Scheduled(cron = "${billing.cron}")
    public void runMonthlyBilling() {
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        log.info("Auto billing triggered for {}/{}",
                lastMonth.getMonthValue(), lastMonth.getYear());
        billingService.generateForAllUsers(
                lastMonth.getYear(), lastMonth.getMonthValue());
    }
}