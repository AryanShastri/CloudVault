package com.cloudvault.storage_engine.controller;

import com.cloudvault.storage_engine.dto.StorageDtos.*;
import com.cloudvault.storage_engine.entity.*;
import com.cloudvault.storage_engine.enums.*;
import com.cloudvault.storage_engine.repository.*;
import com.cloudvault.storage_engine.service.LifecycleService;
import com.cloudvault.storage_engine.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/lifecycle")
@RequiredArgsConstructor
public class LifecycleController {

    private final LifecycleService lifecycleService;
    private final BucketRepository bucketRepository;
    private final UserRepository userRepository;
    private final BucketLifecycleEventRepository lifecycleEventRepository;
    private final RestoreRequestRepository restoreRequestRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final LifecyclePolicyRepository lifecyclePolicyRepository;


    @GetMapping("/buckets/{bucketName}/status")
    public ResponseEntity<?> getStatus(
            Authentication auth,
            @PathVariable String bucketName) {
        try {
            User user = getUser(auth);
            Bucket bucket = getBucket(user, bucketName);

            LifecycleService.BucketLifecycleInfo info =
                    lifecycleService.getLifecycleStatus(bucket);

            List<BucketLifecycleEvent> events =
                    lifecycleEventRepository
                            .findByBucketOrderByTransitionedAtAsc(bucket);

            LifecycleStatusResponse response = new LifecycleStatusResponse();
            response.setBucketName(bucketName);
            response.setCurrentTier(info.currentTier() != null ? info.currentTier().name() : "STANDARD");
            response.setPolicyType(bucket.getPolicyType() != null ? bucket.getPolicyType().name() : "PREDEFINED");
            response.setTierChangedAt(
                    bucket.getTierChangedAt() != null
                            ? bucket.getTierChangedAt().toString() : "N/A");
            response.setLastAccessedAt(
                    bucket.getLastAccessedAt() != null
                            ? bucket.getLastAccessedAt().toString() : "Never");
            response.setDaysInCurrentTier(info.daysInCurrentTier());
            response.setRequestsInPeriod(bucket.getRequestsInPeriod());
            response.setNextDowngradeAt(
                    info.daysUntilNextDowngrade() == Integer.MAX_VALUE
                            ? "Never (DEEP_GLACIER)"
                            : "In " + info.daysUntilNextDowngrade() + " days");
            response.setNextTierIfDowngraded(
                    info.nextTierDown() != null
                            ? info.nextTierDown().name() : "N/A");
            response.setRequestsUntilUpgrade(info.requestsUntilUpgrade());
            response.setUpgradesTo(
                    info.nextTierUp() != null
                            ? info.nextTierUp().name() : "Already STANDARD");
            
            LifecyclePolicy policy = lifecyclePolicyRepository.findByBucketAndActiveTrue(bucket).orElse(null);
            response.setVersioningEnabled(policy != null && policy.isVersioningEnabled());

            // Map recent events
            response.setRecentEvents(events.stream()
                    .map(e -> {
                        LifecycleStatusResponse.LifecycleEventResponse er =
                                new LifecycleStatusResponse.LifecycleEventResponse();
                        er.setFromTier(e.getFromTier() != null ? e.getFromTier().name() : "STANDARD");
                        er.setToTier(e.getToTier() != null ? e.getToTier().name() : "STANDARD");
                        er.setReason(e.getReason() != null ? e.getReason().name() : "UNKNOWN");
                        er.setTransitionedAt(e.getTransitionedAt().toString());
                        er.setDaysInPreviousTier(e.getDaysInPreviousTier());
                        return er;
                    })
                    .collect(Collectors.toList()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            StringBuilder trace = new StringBuilder();
            trace.append(e.toString()).append("\n");
            for(StackTraceElement el : e.getStackTrace()) {
                trace.append(el.toString()).append("\n");
            }
            return ResponseEntity.internalServerError().body(trace.toString());
        }
    }


    @PostMapping("/buckets/{bucketName}/policy")
    @CacheEvict(value = "lifecycleStatus", key = "#auth.getName() + ':' + #bucketName")
    public ResponseEntity<String> setPolicy(
            Authentication auth,
            @PathVariable String bucketName,
            @RequestBody CreateLifecyclePolicyRequest request) {

        User user = getUser(auth);
        Bucket bucket = getBucket(user, bucketName);

        bucket.setPolicyType(request.getPolicyType());
        bucketRepository.save(bucket);

        if (request.getPolicyType() == PolicyType.CUSTOM && request.getTransitionRules() != null) {
            LifecyclePolicy policy = lifecyclePolicyRepository.findByBucketAndActiveTrue(bucket)
                    .orElse(LifecyclePolicy.builder()
                            .bucket(bucket)
                            .policyType(PolicyType.CUSTOM)
                            .active(true)
                            .build());

            if (policy.getTransitionRules() != null) {
                policy.getTransitionRules().clear();
            } else {
                policy.setTransitionRules(new java.util.ArrayList<>());
            }

            for (CreateLifecyclePolicyRequest.TransitionRuleRequest ruleReq : request.getTransitionRules()) {
                LifecycleTransitionRule rule = LifecycleTransitionRule.builder()
                        .policy(policy)
                        .fromTier(LifecycleTier.valueOf(ruleReq.getFromTier()))
                        .toTier(LifecycleTier.valueOf(ruleReq.getToTier()))
                        .daysOfInactivity(ruleReq.getDaysOfInactivity())
                        .build();
                policy.getTransitionRules().add(rule);
            }
            lifecyclePolicyRepository.save(policy);
        } else if (request.getPolicyType() == PolicyType.PREDEFINED) {
            lifecyclePolicyRepository.findByBucketAndActiveTrue(bucket).ifPresent(policy -> {
                policy.setActive(false);
                lifecyclePolicyRepository.save(policy);
            });
        }

        return ResponseEntity.ok("Lifecycle policy set to "
                + request.getPolicyType().name()
                + " for bucket: " + bucketName);
    }


    @PostMapping("/buckets/{bucketName}/restore")
    public ResponseEntity<RestoreStatusResponse> requestRestore(
            Authentication auth,
            @PathVariable String bucketName,
            @RequestBody RestoreRequestDto request) {

        User user = getUser(auth);
        Bucket bucket = getBucket(user, bucketName);

        if (bucket.getCurrentTier() != LifecycleTier.DEEP_GLACIER) {
            return ResponseEntity.badRequest().build();
        }

        StorageObject obj = storageObjectRepository
                .findByBucketAndObjectKeyAndDeletedFalse(
                        bucket, request.getObjectKey())
                .orElseThrow(() -> new RuntimeException(
                        "Object not found: " + request.getObjectKey()));

        RestoreSpeed speed = RestoreSpeed.valueOf(
                request.getRestoreSpeed().toUpperCase());

        RestoreRequest restore = lifecycleService.requestRestore(
                bucket, user, request.getObjectKey(),
                speed, obj.getSizeBytes());

        return ResponseEntity.ok(toRestoreResponse(restore));
    }


    @GetMapping("/buckets/{bucketName}/restore/status")
    public ResponseEntity<List<RestoreStatusResponse>> getRestoreStatus(
            Authentication auth,
            @PathVariable String bucketName) {

        User user = getUser(auth);
        Bucket bucket = getBucket(user, bucketName);

        List<RestoreStatusResponse> responses =
                restoreRequestRepository
                        .findByBucketAndStatus(bucket, RestoreStatus.COMPLETED)
                        .stream()
                        .map(this::toRestoreResponse)
                        .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }


    @GetMapping("/buckets/{bucketName}/history")
    public ResponseEntity<List<LifecycleStatusResponse.LifecycleEventResponse>>
    getHistory(Authentication auth, @PathVariable String bucketName) {

        User user = getUser(auth);
        Bucket bucket = getBucket(user, bucketName);

        List<LifecycleStatusResponse.LifecycleEventResponse> events =
                lifecycleEventRepository
                        .findByBucketOrderByTransitionedAtAsc(bucket)
                        .stream()
                        .map(e -> {
                            LifecycleStatusResponse.LifecycleEventResponse er =
                                    new LifecycleStatusResponse
                                            .LifecycleEventResponse();
                            er.setFromTier(e.getFromTier().name());
                            er.setToTier(e.getToTier().name());
                            er.setReason(e.getReason().name());
                            er.setTransitionedAt(
                                    e.getTransitionedAt().toString());
                            er.setDaysInPreviousTier(
                                    e.getDaysInPreviousTier());
                            return er;
                        })
                        .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }


    @PostMapping("/admin/trigger-check")
    public ResponseEntity<String> triggerCheck() {
        lifecycleService.checkAndTransitionBuckets();
        return ResponseEntity.ok("Lifecycle check triggered");
    }


    @PostMapping("/admin/trigger-restore")
    public ResponseEntity<String> triggerRestore() {
        lifecycleService.processRestoreRequests();
        lifecycleService.checkExpiredRestores();
        return ResponseEntity.ok("Restore processor triggered");
    }

    private RestoreStatusResponse toRestoreResponse(RestoreRequest r) {
        RestoreStatusResponse res = new RestoreStatusResponse();
        res.setId(r.getId());
        res.setObjectKey(r.getObjectKey());
        res.setRestoreSpeed(r.getRestoreSpeed().name());
        res.setStatus(r.getStatus().name());
        res.setRequestedAt(r.getRequestedAt().toString());
        res.setEstimatedCompletion(r.getEstimatedCompletion().toString());
        res.setCompletedAt(r.getCompletedAt() != null
                ? r.getCompletedAt().toString() : null);
        res.setAccessExpiresAt(r.getAccessExpiresAt() != null
                ? r.getAccessExpiresAt().toString() : null);
        res.setRestoreFeeCharged(
                r.getRestoreFeeCharged().doubleValue());
        res.setMessage(switch (r.getStatus()) {
            case PENDING     -> "Restore in progress. Check back later.";
            case IN_PROGRESS -> "Restore being processed.";
            case COMPLETED   -> "Object available for download until "
                    + r.getAccessExpiresAt();
            case EXPIRED     -> "Access window expired. Request new restore.";
            case FAILED      -> "Restore failed. Please try again.";
        });
        return res;
    }

    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Bucket getBucket(User user, String bucketName) {
        return bucketRepository
                .findByUserAndNameAndActiveTrue(user, bucketName)
                .orElseThrow(() -> new RuntimeException(
                        "Bucket not found: " + bucketName));
    }

    @GetMapping("/buckets/{bucketName}/policy")
    public ResponseEntity<Map<String, Object>> getPolicy(
            Authentication auth,
            @PathVariable String bucketName) {

        User user = getUser(auth);
        Bucket bucket = getBucket(user, bucketName);

        Map<String, Object> response = new LinkedHashMap<>();
        PolicyType pType = bucket.getPolicyType() != null ? bucket.getPolicyType() : PolicyType.PREDEFINED;
        
        response.put("bucketName", bucketName);
        response.put("policyType", pType.name());

        if (pType == PolicyType.PREDEFINED) {

            Map<String, Object> predefined = new LinkedHashMap<>();
            predefined.put("standardToWarmDays", 30);
            predefined.put("warmToInstantGlacierDays", 60);
            predefined.put("instantGlacierToDeepGlacierDays", 90);
            predefined.put("deepGlacierMaxRequestsPer180Days", 3);
            predefined.put("instantGlacierMaxRequestsPer30Days", 6);
            predefined.put("warmMaxRequestsPer30Days", 9);
            response.put("predefinedRules", predefined);
        } else {

            LifecyclePolicy policy = lifecyclePolicyRepository
                    .findByBucketAndActiveTrue(bucket)
                    .orElse(null);

            if (policy != null) {
                List<Map<String, Object>> rules = policy.getTransitionRules()
                        .stream()
                        .map(r -> {
                            Map<String, Object> rule = new LinkedHashMap<>();
                            rule.put("fromTier", r.getFromTier().name());
                            rule.put("toTier", r.getToTier().name());
                            rule.put("daysOfInactivity", r.getDaysOfInactivity());
                            return rule;
                        })
                        .collect(Collectors.toList());

                response.put("customRules", rules);
                response.put("versioningEnabled", policy.isVersioningEnabled());
                response.put("expirationDays", policy.getExpirationDays());
            } else {
                response.put("customRules", List.of());
                response.put("message", "No custom policy set yet");
            }
        }

        return ResponseEntity.ok(response);
    }
}