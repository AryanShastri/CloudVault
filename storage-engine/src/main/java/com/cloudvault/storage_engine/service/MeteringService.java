package com.cloudvault.storage_engine.service;

import com.cloudvault.storage_engine.entity.Bucket;
import com.cloudvault.storage_engine.entity.UsageRecord;
import com.cloudvault.storage_engine.entity.User;
import com.cloudvault.storage_engine.enums.OperationType;
import com.cloudvault.storage_engine.repository.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeteringService {

    private final UsageRecordRepository usageRecordRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User user, Bucket bucket, OperationType opType,
                       long bytes, long bandwidth, String objectKey) {
        try {
            LocalDate now = LocalDate.now();
            UsageRecord record = UsageRecord.builder()
                    .user(user)
                    .bucket(bucket)
                    .operationType(opType)
                    .bytes(bytes)
                    .bandwidthBytes(bandwidth)
                    .objectKey(objectKey)
                    .billingYear(now.getYear())
                    .billingMonth(now.getMonthValue())
                    .build();

            usageRecordRepository.save(record);
            log.debug("Metered: user={} op={} bytes={} bandwidth={}",
                    user.getUsername(), opType, bytes, bandwidth);
        } catch (Exception e) {
            log.error("Failed to record usage: user={} op={} error={}",
                    user.getUsername(), opType, e.getMessage());
        }
    }
}