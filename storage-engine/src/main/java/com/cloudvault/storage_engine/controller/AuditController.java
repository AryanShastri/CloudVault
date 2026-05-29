package com.cloudvault.storage_engine.controller;

import com.cloudvault.storage_engine.dto.BillingDtos.*;
import com.cloudvault.storage_engine.entity.*;
import com.cloudvault.storage_engine.enums.OperationType;
import com.cloudvault.storage_engine.repository.*;
import com.cloudvault.storage_engine.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final UsageRecordRepository usageRecordRepository;
    private final BucketRepository bucketRepository;
    private final UserRepository userRepository;


    @GetMapping("/logs")
    public ResponseEntity<Page<AuditLogResponse>> getLogs(
            Authentication auth,
            @PageableDefault(size = 20, sort = "recordedAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        User user = getUser(auth);
        return ResponseEntity.ok(
                usageRecordRepository
                        .findByUserAndOperationTypeNotOrderByRecordedAtDesc(user, OperationType.LIST, pageable)
                        .map(this::toAuditResponse));
    }


    @GetMapping("/logs/bucket/{bucketName}")
    public ResponseEntity<Page<AuditLogResponse>> getBucketLogs(
            Authentication auth,
            @PathVariable String bucketName,
            @PageableDefault(size = 20, sort = "recordedAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        User user = getUser(auth);
        Bucket bucket = bucketRepository
                .findByUserAndNameAndActiveTrue(user, bucketName)
                .orElseThrow(() -> new RuntimeException(
                        "Bucket not found: " + bucketName));
        return ResponseEntity.ok(
                usageRecordRepository
                        .findByUserAndBucketAndOperationTypeNotOrderByRecordedAtDesc(
                                user, bucket, OperationType.LIST, pageable)
                        .map(this::toAuditResponse));
    }


    @GetMapping("/logs/object/{objectKey}")
    public ResponseEntity<List<AuditLogResponse>> getObjectLogs(
            Authentication auth,
            @PathVariable String objectKey) {
        User user = getUser(auth);
        return ResponseEntity.ok(
                usageRecordRepository
                        .findByUserAndObjectKeyAndOperationTypeNotOrderByRecordedAtDesc(
                                user, objectKey, OperationType.LIST)
                        .stream()
                        .map(this::toAuditResponse)
                        .collect(Collectors.toList()));
    }

    private AuditLogResponse toAuditResponse(UsageRecord r) {
        AuditLogResponse res = new AuditLogResponse();
        res.setId(r.getId());
        res.setOperationType(r.getOperationType().name());
        res.setRequestClass(r.getOperationType().getRequestClass().name());
        res.setBucketName(r.getBucket() != null
                ? r.getBucket().getName() : "N/A");
        res.setObjectKey(r.getObjectKey());
        res.setSizeFormatted(StorageService.formatBytes(r.getBytes()));
        res.setBandwidthFormatted(
                StorageService.formatBytes(r.getBandwidthBytes()));
        res.setTierAtTimeOfRequest(r.getTierAtTimeOfRequest() != null
                ? r.getTierAtTimeOfRequest().name() : "STANDARD");
        res.setRecordedAt(r.getRecordedAt().toString());


        res.setTimestamp(r.getRecordedAt().toString());
        res.setSizeBytes(r.getBytes());
        res.setTierAtTime(r.getTierAtTimeOfRequest() != null
                ? r.getTierAtTimeOfRequest().name() : "STANDARD");
        return res;
    }

    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}