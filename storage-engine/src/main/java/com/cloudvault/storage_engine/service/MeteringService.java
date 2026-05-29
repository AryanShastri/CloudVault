package com.cloudvault.storage_engine.service;

import com.cloudvault.storage_engine.entity.Bucket;
import com.cloudvault.storage_engine.entity.UsageRecord;
import com.cloudvault.storage_engine.entity.User;
import com.cloudvault.storage_engine.enums.LifecycleTier;
import com.cloudvault.storage_engine.enums.OperationType;
import com.cloudvault.storage_engine.repository.BucketRepository;
import com.cloudvault.storage_engine.repository.UsageRecordRepository;
import com.cloudvault.storage_engine.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

@Service
@Slf4j
public class MeteringService {

    private final UsageRecordRepository usageRecordRepository;
    private final UserRepository userRepository;
    private final BucketRepository bucketRepository;
    private final LifecycleService lifecycleService;
    private final CacheManager cacheManager;
    private final TransactionTemplate requiresNewTx;

    public MeteringService(UsageRecordRepository usageRecordRepository,
                           UserRepository userRepository,
                           BucketRepository bucketRepository,
                           LifecycleService lifecycleService,
                           CacheManager cacheManager,
                           PlatformTransactionManager transactionManager) {
        this.usageRecordRepository = usageRecordRepository;
        this.userRepository = userRepository;
        this.bucketRepository = bucketRepository;
        this.lifecycleService = lifecycleService;
        this.cacheManager = cacheManager;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }


    @Async
    public void record(User user, Bucket bucket, OperationType opType,
                       long bytes, long bandwidth, String objectKey) {
        Long userId = user.getId();
        Long bucketId = bucket != null ? bucket.getId() : null;

        try {
            requiresNewTx.executeWithoutResult(status ->
                    saveUsageRecord(userId, bucketId, opType, bytes, bandwidth, objectKey));
        } catch (Exception e) {
            log.error("Failed to record usage: userId={} op={} error={}",
                    userId, opType, e.getMessage(), e);
            return;
        }

        if (bucketId != null && opType != OperationType.LIST) {
            try {
                Bucket managedBucket = bucketRepository.findById(bucketId).orElse(null);
                if (managedBucket != null) {
                    lifecycleService.onBucketAccessed(managedBucket);
                }
            } catch (Exception e) {
                log.warn("Lifecycle update skipped for bucket {}: {}",
                        bucketId, e.getMessage());
            }
        }

        evictUsageCache(userId);
        log.debug("Metered: userId={} op={} bytes={}", userId, opType, bytes);
    }

    private void saveUsageRecord(Long userId, Long bucketId, OperationType opType,
                                 long bytes, long bandwidth, String objectKey) {
        LocalDate now = LocalDate.now();
        User managedUser = userRepository.getReferenceById(userId);
        Bucket managedBucket = bucketId != null
                ? bucketRepository.getReferenceById(bucketId)
                : null;

        LifecycleTier tierNow = managedBucket != null
                ? managedBucket.getCurrentTier()
                : null;

        UsageRecord record = UsageRecord.builder()
                .user(managedUser)
                .bucket(managedBucket)
                .operationType(opType)
                .bytes(bytes)
                .bandwidthBytes(bandwidth)
                .objectKey(objectKey)
                .tierAtTimeOfRequest(tierNow)
                .billingYear(now.getYear())
                .billingMonth(now.getMonthValue())
                .build();

        usageRecordRepository.saveAndFlush(record);
    }

    private void evictUsageCache(Long userId) {
        if (cacheManager == null || userId == null) {
            return;
        }
        try {
            var usageCache = cacheManager.getCache("currentUsage");
            if (usageCache != null) {
                usageCache.evict(userId);
            }
            var invoiceCache = cacheManager.getCache("invoices");
            if (invoiceCache != null) {
                invoiceCache.evict(userId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict billing caches for user {}: {}",
                    userId, e.getMessage());
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordVersionDownload(User user, Bucket bucket,
                                      long bytes, long bandwidth,
                                      String objectKey,
                                      boolean isNoncurrent) {
        try {
            LocalDate now = LocalDate.now();
            LifecycleTier tierNow = bucket.getCurrentTier();

            UsageRecord record = UsageRecord.builder()
                    .user(user)
                    .bucket(bucket)
                    .operationType(OperationType.GET)
                    .bytes(bytes)
                    .bandwidthBytes(bandwidth)
                    .objectKey(objectKey)
                    .tierAtTimeOfRequest(tierNow)
                    .billingYear(now.getYear())
                    .billingMonth(now.getMonthValue())
                    .noncurrentVersionDownload(isNoncurrent) // ← flag
                    .build();

            usageRecordRepository.save(record);
            log.debug("Version download metered: user={} " +
                            "noncurrent={} bytes={}",
                    user.getUsername(), isNoncurrent, bandwidth);

        } catch (Exception e) {
            log.error("Failed to record version download: {}",
                    e.getMessage());
        }
    }
}
