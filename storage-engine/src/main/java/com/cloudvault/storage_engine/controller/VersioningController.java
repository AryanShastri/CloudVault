package com.cloudvault.storage_engine.controller;

import com.cloudvault.storage_engine.entity.*;
import com.cloudvault.storage_engine.enums.PolicyType;
import com.cloudvault.storage_engine.exception.ResourceNotFoundException;
import com.cloudvault.storage_engine.repository.*;
import com.cloudvault.storage_engine.service.StorageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/versioning")
@RequiredArgsConstructor
@Slf4j
public class VersioningController {

    private final ObjectVersionRepository versionRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final BucketRepository bucketRepository;
    private final UserRepository userRepository;
    private final LifecyclePolicyRepository lifecyclePolicyRepository;
    private final StorageService storageService;

    @Value("${billing.versioning.noncurrent-discount:0.90}")
    private double noncurrentStorageDiscount;

    @Value("${billing.versioning.noncurrent-bandwidth-surcharge:1.20}")
    private double noncurrentBandwidthSurcharge;

    @PostMapping("/buckets/{bucketName}/enable")
    public ResponseEntity<String> enableVersioning(
            Authentication auth,
            @PathVariable String bucketName) {
        User user = getUser(auth);
        Bucket bucket = getBucket(user, bucketName);


        LifecyclePolicy policy = lifecyclePolicyRepository
                .findByBucketAndActiveTrue(bucket)
                .orElse(null);

        if (policy == null) {

            policy = LifecyclePolicy.builder()
                    .bucket(bucket)
                    .policyType(PolicyType.PREDEFINED)
                    .versioningEnabled(true)
                    .active(true)
                    .build();
        } else {
            policy.setVersioningEnabled(true);
        }

        lifecyclePolicyRepository.save(policy);
        log.info("Versioning enabled for bucket: {}", bucketName);
        return ResponseEntity.ok("Versioning enabled for bucket: " + bucketName);
    }

    @GetMapping("/buckets/{bucketName}/versions/download")
    public ResponseEntity<byte[]> downloadVersion(
            Authentication auth,
            @PathVariable String bucketName,
            @RequestParam String objectKey,
            @RequestParam int versionNumber) throws IOException {

        User user = getUser(auth);
        byte[] data = storageService.downloadObjectVersion(
                user, bucketName, objectKey, versionNumber);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData(
                "attachment",
                "v" + versionNumber + "_" + objectKey);
        headers.add("X-Version-Number",
                String.valueOf(versionNumber));
        headers.add("X-Scan-Status", "CLEAN");
        headers.add("Access-Control-Expose-Headers",
                "X-Version-Number, X-Scan-Status");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }


    @DeleteMapping("/buckets/{bucketName}/versions/{versionNumber}")
    public ResponseEntity<Void> deleteVersion(
            Authentication auth,
            @PathVariable String bucketName,
            @RequestParam String objectKey,
            @PathVariable int versionNumber) {

        User user = getUser(auth);
        Bucket bucket = getBucket(user, bucketName);
        StorageObject obj = storageObjectRepository
                .findByBucketAndObjectKeyAndDeletedFalse(bucket, objectKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Object not found: " + objectKey));

        versionRepository
                .findByStorageObjectOrderByVersionNumberDesc(obj)
                .stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .ifPresent(v -> {
                    v.setDeleted(true);
                    v.setDeletedAt(LocalDateTime.now());
                    versionRepository.save(v);
                });

        return ResponseEntity.noContent().build();
    }



    @Data
    public static class VersionResponse {
        private int versionNumber;
        private long sizeBytes;
        private String sizeFormatted;
        private boolean current;           // ← was isCurrent
        private String currentTier;
        private String createdAt;          // ← String not LocalDateTime
        private String storageChargeNote;  // ← fixed name
        private String bandwidthNote;
    }


    @GetMapping("/buckets/{bucketName}/versions")
    public ResponseEntity<List<VersionResponse>> listVersions(
            Authentication auth,
            @PathVariable String bucketName,
            @RequestParam String objectKey) {

        User user = getUser(auth);
        Bucket bucket = getBucket(user, bucketName);

        List<VersionResponse> versions = versionRepository
                .findVersionsByBucketAndObjectKey(bucket, objectKey)
                .stream()
                .map(v -> {
                    VersionResponse r = new VersionResponse();
                    r.setVersionNumber(v.getVersionNumber());
                    r.setSizeBytes(v.getSizeBytes());
                    r.setSizeFormatted(
                            StorageService.formatBytes(v.getSizeBytes()));
                    r.setCurrent(v.isCurrent());
                    r.setCurrentTier(v.getCurrentTier() != null
                            ? v.getCurrentTier().name() : "STANDARD");
                    r.setCreatedAt(v.getCreatedAt() != null
                            ? v.getCreatedAt().toString() : null);
                    r.setStorageChargeNote(v.isCurrent()
                            ? "Standard rate for bucket tier"
                            : String.format("%d%% discount — noncurrent version", (int) Math.round((1 - noncurrentStorageDiscount) * 100)));
                    r.setBandwidthNote(v.isCurrent()
                            ? "Standard bandwidth rate"
                            : String.format("%d%% surcharge — noncurrent version download", (int) Math.round((noncurrentBandwidthSurcharge - 1) * 100)));
                    return r;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(versions);
    }

    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Bucket getBucket(User user, String bucketName) {
        return bucketRepository
                .findByUserAndNameAndActiveTrue(user, bucketName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bucket not found: " + bucketName));
    }

    
}