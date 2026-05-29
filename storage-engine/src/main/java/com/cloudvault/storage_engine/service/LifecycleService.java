package com.cloudvault.storage_engine.service;


import com.cloudvault.storage_engine.entity.*;
import com.cloudvault.storage_engine.enums.*;
import com.cloudvault.storage_engine.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class LifecycleService {

    private final BucketRepository bucketRepository;
    private final BucketLifecycleEventRepository lifecycleEventRepository;
    private final LifecyclePolicyRepository policyRepository;
    private final RestoreRequestRepository restoreRequestRepository;
    private final UserRepository userRepository;



    @Value("${lifecycle.predefined.standard-to-warm-days:30}")
    private int standardToWarmDays;

    @Value("${lifecycle.predefined.warm-to-instant-glacier-days:60}")
    private int warmToInstantGlacierDays;

    @Value("${lifecycle.predefined.instant-glacier-to-deep-glacier-days:90}")
    private int instantGlacierToDeepGlacierDays;



    @Value("${lifecycle.predefined.deep-glacier-max-requests:1}")
    private int deepGlacierMaxRequests;

    @Value("${lifecycle.predefined.deep-glacier-window-days:180}")
    private int deepGlacierWindowDays;

    @Value("${lifecycle.predefined.instant-glacier-max-requests:3}")
    private int instantGlacierMaxRequests;

    @Value("${lifecycle.predefined.instant-glacier-window-days:30}")
    private int instantGlacierWindowDays;

    @Value("${lifecycle.predefined.warm-max-requests:6}")
    private int warmMaxRequests;

    @Value("${lifecycle.predefined.warm-window-days:30}")
    private int warmWindowDays;


    @Value("${lifecycle.minimum-duration.warm:30}")
    private int minDurationWarm;

    @Value("${lifecycle.minimum-duration.instant-glacier:90}")
    private int minDurationInstantGlacier;

    @Value("${lifecycle.minimum-duration.deep-glacier:180}")
    private int minDurationDeepGlacier;



    @Value("${lifecycle.restore.expedited.demo-minutes:1}")
    private int expeditedDemoMinutes;

    @Value("${lifecycle.restore.standard.demo-minutes:5}")
    private int standardDemoMinutes;

    @Value("${lifecycle.restore.bulk.demo-minutes:10}")
    private int bulkDemoMinutes;

    @Value("${lifecycle.restore.expedited.access-hours:24}")
    private int expeditedAccessHours;

    @Value("${lifecycle.restore.standard.access-hours:72}")
    private int standardAccessHours;

    @Value("${lifecycle.restore.bulk.access-hours:168}")
    private int bulkAccessHours;

    @Value("${lifecycle.restore.expedited.per-gb:0.03}")
    private double expeditedPerGb;

    @Value("${lifecycle.restore.standard.per-gb:0.02}")
    private double standardPerGb;

    @Value("${lifecycle.restore.bulk.per-gb:0.0025}")
    private double bulkPerGb;

    @Value("${lifecycle.restore.expedited.per-request:0.01}")
    private double expeditedPerRequest;

    @Value("${lifecycle.restore.standard.per-request:0.0025}")
    private double standardPerRequest;

    private static final double BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0;




    @Transactional
    public void checkAndTransitionBuckets() {
        log.info("Running daily lifecycle downgrade check");

        List<Bucket> buckets = bucketRepository.findAll()
                .stream()
                .filter(Bucket::isActive)
                .toList();

        int transitioned = 0;
        for (Bucket bucket : buckets) {
            try {
                boolean moved = checkDowngrade(bucket);
                if (moved) transitioned++;
            } catch (Exception e) {
                log.error("Lifecycle check failed for bucket {}: {}",
                        bucket.getName(), e.getMessage());
            }
        }

        log.info("Lifecycle check complete. {} buckets checked. {} transitioned.",
                buckets.size(), transitioned);
    }


    private boolean checkDowngrade(Bucket bucket) {

        if (bucket.getCurrentTier() == LifecycleTier.DEEP_GLACIER) {
            return false;
        }


        LocalDateTime lastActivity = bucket.getLastAccessedAt() != null
                ? bucket.getLastAccessedAt()
                : bucket.getCreatedAt();

        if (lastActivity == null) return false;

        long daysInactive = ChronoUnit.DAYS.between(
                lastActivity, LocalDateTime.now());

        LifecycleTier currentTier = bucket.getCurrentTier();
        LifecycleTier newTier;

        if (bucket.getPolicyType() == PolicyType.PREDEFINED) {
            newTier = checkPredefinedDowngrade(currentTier, daysInactive);
        } else {
            newTier = checkCustomDowngrade(bucket, currentTier, daysInactive);
        }

        if (newTier != null && newTier != currentTier) {
            transitionBucket(bucket, newTier, TransitionReason.INACTIVITY);
            return true;
        }

        return false;
    }


    private LifecycleTier checkPredefinedDowngrade(
            LifecycleTier current, long daysInactive) {
        return switch (current) {
            case STANDARD ->
                    daysInactive >= standardToWarmDays
                            ? LifecycleTier.WARM : null;
            case WARM ->
                    daysInactive >= warmToInstantGlacierDays
                            ? LifecycleTier.INSTANT_GLACIER : null;
            case INSTANT_GLACIER ->
                    daysInactive >= instantGlacierToDeepGlacierDays
                            ? LifecycleTier.DEEP_GLACIER : null;
            case DEEP_GLACIER -> null;
        };
    }


    private LifecycleTier checkCustomDowngrade(
            Bucket bucket, LifecycleTier current, long daysInactive) {
        return policyRepository.findByBucketAndActiveTrue(bucket)
                .map(policy -> policy.getTransitionRules().stream()
                        .filter(r ->
                                r.getFromTier() == current
                                        && r.getDaysOfInactivity() != null
                                        && daysInactive >= r.getDaysOfInactivity())
                        .map(LifecycleTransitionRule::getToTier)
                        .findFirst()
                        .orElse(null))
                .orElse(null);
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBucketAccessed(Bucket bucket) {

        Bucket freshBucket = bucketRepository
                .findByIdWithLock(bucket.getId())
                .orElse(null);
        if (freshBucket == null) return;


        LocalDateTime now = LocalDateTime.now();
        freshBucket.setLastAccessedAt(now);

        if (freshBucket.getPeriodStartAt() == null) {
            freshBucket.setPeriodStartAt(now);
            freshBucket.setRequestsInPeriod(0);
        }

        if (isWindowExpired(freshBucket, now)) {
            freshBucket.setRequestsInPeriod(0);
            freshBucket.setPeriodStartAt(now);
        }

        freshBucket.setRequestsInPeriod(freshBucket.getRequestsInPeriod() + 1);

        LifecycleTier upgradeTarget = checkUpgradeThreshold(freshBucket);
        if (upgradeTarget != null) {
            transitionBucket(freshBucket, upgradeTarget,
                    TransitionReason.EXCEEDED_REQUESTS);
            freshBucket.setRequestsInPeriod(0);
            freshBucket.setPeriodStartAt(now);
        }

        bucketRepository.save(freshBucket);
    }

    
    private boolean isWindowExpired(Bucket bucket, LocalDateTime now) {
        if (bucket.getPeriodStartAt() == null) return true;

        long daysSincePeriodStart = ChronoUnit.DAYS.between(
                bucket.getPeriodStartAt(), now);

        int windowDays = switch (bucket.getCurrentTier()) {
            case DEEP_GLACIER    -> deepGlacierWindowDays;
            case INSTANT_GLACIER -> instantGlacierWindowDays;
            case WARM            -> warmWindowDays;
            case STANDARD        -> warmWindowDays;
        };

        return daysSincePeriodStart >= windowDays;
    }


    private LifecycleTier checkUpgradeThreshold(Bucket bucket) {
        int requests = bucket.getRequestsInPeriod();
        LifecycleTier current = bucket.getCurrentTier();

        return switch (current) {
            case DEEP_GLACIER ->
                    requests > deepGlacierMaxRequests
                            ? LifecycleTier.INSTANT_GLACIER : null;
            case INSTANT_GLACIER ->
                    requests > instantGlacierMaxRequests
                            ? LifecycleTier.WARM : null;
            case WARM ->
                    requests > warmMaxRequests
                            ? LifecycleTier.STANDARD : null;
            case STANDARD -> null;
        };
    }


    @Transactional
    public void transitionBucket(Bucket bucket,
                                 LifecycleTier newTier,
                                 TransitionReason reason) {
        LifecycleTier oldTier = bucket.getCurrentTier();
        LocalDateTime now = LocalDateTime.now();


        int daysInPreviousTier = 0;
        if (bucket.getTierChangedAt() != null) {
            daysInPreviousTier = (int) ChronoUnit.DAYS.between(
                    bucket.getTierChangedAt(), now);
        }


        BucketLifecycleEvent event = BucketLifecycleEvent.builder()
                .bucket(bucket)
                .user(bucket.getUser())
                .fromTier(oldTier)
                .toTier(newTier)
                .reason(reason)
                .daysInPreviousTier(daysInPreviousTier)
                .earlyDeletionCharge(BigDecimal.ZERO)
                .build();

        lifecycleEventRepository.save(event);


        bucket.setCurrentTier(newTier);
        bucket.setTierChangedAt(now);
        bucket.setRequestsInPeriod(0);
        bucket.setPeriodStartAt(now);
        bucketRepository.save(bucket);

        log.info("Bucket [{}] transitioned {} → {} | reason={} | daysInPrev={}",
                bucket.getName(), oldTier, newTier,
                reason, daysInPreviousTier);
    }


    @Transactional
    public RestoreRequest requestRestore(Bucket bucket,
                                         User user,
                                         String objectKey,
                                         RestoreSpeed speed,
                                         long objectSizeBytes) {

        restoreRequestRepository
                .findByBucketAndObjectKeyAndStatus(
                        bucket, objectKey, RestoreStatus.PENDING)
                .ifPresent(existing -> {
                    throw new RuntimeException(
                            "Restore already in progress for: " + objectKey
                                    + ". Wait for it to complete before requesting again.");
                });

        LocalDateTime now = LocalDateTime.now();


        LocalDateTime estimatedCompletion = switch (speed) {
            case EXPEDITED -> now.plusMinutes(expeditedDemoMinutes);
            case STANDARD  -> now.plusMinutes(standardDemoMinutes);
            case BULK      -> now.plusMinutes(bulkDemoMinutes);
        };


        LocalDateTime accessExpires = switch (speed) {
            case EXPEDITED -> estimatedCompletion.plusMinutes(2);
            case STANDARD  -> estimatedCompletion.plusHours(standardAccessHours);
            case BULK      -> estimatedCompletion.plusHours(bulkAccessHours);
        };


        double gbSize = objectSizeBytes / BYTES_PER_GB;
        double feePerGb = switch (speed) {
            case EXPEDITED -> expeditedPerGb;
            case STANDARD  -> standardPerGb;
            case BULK      -> bulkPerGb;
        };
        double feePerRequest = switch (speed) {
            case EXPEDITED -> expeditedPerRequest;
            case STANDARD  -> standardPerRequest;
            case BULK      -> 0.0;
        };

        BigDecimal fee = BigDecimal.valueOf(
                        (gbSize * feePerGb) + feePerRequest)
                .setScale(6, RoundingMode.HALF_UP);

        RestoreRequest request = RestoreRequest.builder()
                .bucket(bucket)
                .user(user)
                .objectKey(objectKey)
                .restoreSpeed(speed)
                .status(RestoreStatus.PENDING)
                .estimatedCompletion(estimatedCompletion)
                .accessExpiresAt(accessExpires)
                .restoredBytes(objectSizeBytes)
                .restoreFeeCharged(fee)
                .build();

        request = restoreRequestRepository.save(request);

        log.info("Restore requested: bucket={} key={} speed={} size={}GB fee=${}",
                bucket.getName(), objectKey, speed,
                String.format("%.4f", gbSize), fee);

        return request;
    }


    @Transactional
    public void processRestoreRequests() {
        List<RestoreRequest> pending = restoreRequestRepository
                .findByStatus(RestoreStatus.PENDING);

        if (pending.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        int completed = 0;

        for (RestoreRequest req : pending) {
            if (now.isAfter(req.getEstimatedCompletion())) {
                req.setStatus(RestoreStatus.COMPLETED);
                req.setCompletedAt(now);
                restoreRequestRepository.save(req);
                completed++;
                log.info("Restore completed: bucket={} key={} speed={}",
                        req.getBucket().getName(),
                        req.getObjectKey(),
                        req.getRestoreSpeed());
            }
        }

        if (completed > 0) {
            log.info("{} restore requests completed", completed);
        }
    }


    @Transactional
    public void checkExpiredRestores() {
        List<RestoreRequest> completed = restoreRequestRepository
                .findByStatus(RestoreStatus.COMPLETED);

        if (completed.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        int expired = 0;

        for (RestoreRequest req : completed) {
            if (req.getAccessExpiresAt() != null
                    && now.isAfter(req.getAccessExpiresAt())) {
                req.setStatus(RestoreStatus.EXPIRED);
                restoreRequestRepository.save(req);
                expired++;
                log.info("Restore expired: bucket={} key={}",
                        req.getBucket().getName(),
                        req.getObjectKey());
            }
        }

        if (expired > 0) {
            log.info("{} restore access windows expired", expired);
        }
    }


    public boolean isObjectRestored(Bucket bucket, String objectKey) {
        return restoreRequestRepository
                .findByBucketAndObjectKeyAndStatus(
                        bucket, objectKey, RestoreStatus.COMPLETED)
                .isPresent();
    }


    public BucketLifecycleInfo getLifecycleStatus(Bucket bucket) {
        LocalDateTime now = LocalDateTime.now();
        LifecycleTier current = bucket.getCurrentTier() != null ? bucket.getCurrentTier() : LifecycleTier.STANDARD;


        int daysInCurrentTier = bucket.getTierChangedAt() != null
                ? (int) ChronoUnit.DAYS.between(bucket.getTierChangedAt(), now)
                : 0;


        int daysInactive = 0;
        if (bucket.getLastAccessedAt() != null) {
            daysInactive = (int) ChronoUnit.DAYS.between(
                    bucket.getLastAccessedAt(), now);
        }


        int inactivityThreshold = getInactivityThreshold(bucket);
        int daysUntilDowngrade = inactivityThreshold == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : Math.max(0, inactivityThreshold - daysInactive);


        int maxRequests = getMaxRequestsForTier(current);
        int requestsUntilUpgrade = maxRequests == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : Math.max(0, maxRequests - bucket.getRequestsInPeriod());

        return new BucketLifecycleInfo(
                current,
                daysInCurrentTier,
                daysUntilDowngrade,
                getNextTierDown(current),
                requestsUntilUpgrade,
                getNextTierUp(current),
                bucket.getRequestsInPeriod(),
                daysInactive
        );
    }

    
    private int getInactivityThreshold(Bucket bucket) {
        PolicyType policy = bucket.getPolicyType() != null ? bucket.getPolicyType() : PolicyType.PREDEFINED;
        LifecycleTier current = bucket.getCurrentTier() != null ? bucket.getCurrentTier() : LifecycleTier.STANDARD;

        if (policy == PolicyType.CUSTOM) {
            return policyRepository.findByBucketAndActiveTrue(bucket)
                    .map(p -> p.getTransitionRules().stream()
                            .filter(r -> r.getFromTier() == current
                                    && r.getDaysOfInactivity() != null)
                            .mapToInt(LifecycleTransitionRule::getDaysOfInactivity)
                            .findFirst()
                            .orElse(Integer.MAX_VALUE))
                    .orElse(Integer.MAX_VALUE);
        }


        return switch (current) {
            case STANDARD        -> standardToWarmDays;
            case WARM            -> warmToInstantGlacierDays;
            case INSTANT_GLACIER -> instantGlacierToDeepGlacierDays;
            case DEEP_GLACIER    -> Integer.MAX_VALUE;
        };
    }


    private int getMaxRequestsForTier(LifecycleTier tier) {
        return switch (tier) {
            case DEEP_GLACIER    -> deepGlacierMaxRequests;
            case INSTANT_GLACIER -> instantGlacierMaxRequests;
            case WARM            -> warmMaxRequests;
            case STANDARD        -> Integer.MAX_VALUE;
        };
    }

    private LifecycleTier getNextTierDown(LifecycleTier tier) {
        return switch (tier) {
            case STANDARD        -> LifecycleTier.WARM;
            case WARM            -> LifecycleTier.INSTANT_GLACIER;
            case INSTANT_GLACIER -> LifecycleTier.DEEP_GLACIER;
            case DEEP_GLACIER    -> null;
        };
    }

    private LifecycleTier getNextTierUp(LifecycleTier tier) {
        return switch (tier) {
            case DEEP_GLACIER    -> LifecycleTier.INSTANT_GLACIER;
            case INSTANT_GLACIER -> LifecycleTier.WARM;
            case WARM            -> LifecycleTier.STANDARD;
            case STANDARD        -> null;
        };
    }


    public record BucketLifecycleInfo(
            LifecycleTier currentTier,
            int daysInCurrentTier,
            int daysUntilNextDowngrade,
            LifecycleTier nextTierDown,
            int requestsUntilUpgrade,
            LifecycleTier nextTierUp,
            int currentRequestCount,
            int daysInactive
    ) {}
}