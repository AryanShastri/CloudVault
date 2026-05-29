package com.cloudvault.storage_engine.service;

import com.cloudvault.storage_engine.dto.ScanResult;
import com.cloudvault.storage_engine.entity.*;
import com.cloudvault.storage_engine.entity.Bucket;
import com.cloudvault.storage_engine.entity.ObjectVersion;
import com.cloudvault.storage_engine.enums.LifecycleTier;
import com.cloudvault.storage_engine.enums.OperationType;
import com.cloudvault.storage_engine.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncUploadService {

    private final S3Client s3Client;
    private final BucketRepository bucketRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final UploadJobRepository uploadJobRepository;
    private final LifecyclePolicyRepository lifecyclePolicyRepository;
    private final ObjectVersionRepository versionRepository;
    private final MeteringService meteringService;
    private final CacheManager cacheManager;
    private final VirusScanService virusScanService;

    @Value("${minio.bucket}")
    private String rootBucket;


    private static final String TEMP_PREFIX = "temp-scan/";

    @Async("uploadExecutor")
    @Transactional
    public void processUpload(UploadJob job, User user,
                              String bucketName,
                              byte[] fileBytes,
                              String originalFilename,
                              String contentType,
                              long fileSize,
                              String objectKey) {

        log.info("Starting async upload: jobId={} bucket={} file={}",
                job.getJobId(), bucketName, originalFilename);


        String tempKey = TEMP_PREFIX + user.getTenantId() + "/"
                + UUID.randomUUID() + "_" + sanitizeKey(originalFilename);

        job.setStatus("UPLOADING");
        job.setProgressPercent(5);
        uploadJobRepository.save(job);

        try {
            String resolvedContentType = (contentType == null
                    || contentType.isBlank())
                    ? "application/octet-stream" : contentType;


            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(rootBucket)
                            .key(tempKey)
                            .contentType(resolvedContentType)
                            .contentLength(fileSize)
                            .build(),
                    RequestBody.fromBytes(fileBytes));

            log.info("Uploaded to temp: jobId={} tempKey={}",
                    job.getJobId(), tempKey);

            job.setProgressPercent(30);
            uploadJobRepository.save(job);


            fileBytes = null;
            System.gc();


            job.setStatus("SCANNING");
            job.setProgressPercent(40);
            uploadJobRepository.save(job);

            log.info("Scanning from temp storage: jobId={} file={}",
                    job.getJobId(), originalFilename);

            ScanResult scanResult;
            try (var stream = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(rootBucket)
                            .key(tempKey)
                            .build())) {
                scanResult = virusScanService.scanStream(
                        stream, originalFilename);
            }

            job.setProgressPercent(70);
            uploadJobRepository.save(job);


            if (!scanResult.isClean()) {
                log.warn("INFECTED: jobId={} file={} virus={}",
                        job.getJobId(), originalFilename,
                        scanResult.getVirusName());


                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(rootBucket)
                        .key(tempKey)
                        .build());

                job.setStatus("REJECTED");
                job.setErrorMessage("Malware detected: "
                        + scanResult.getVirusName()
                        + ". File was not saved.");
                job.setCompletedAt(LocalDateTime.now());
                uploadJobRepository.save(job);
                return;
            }


            log.info("File clean — moving to bucket: jobId={}",
                    job.getJobId());

            job.setStatus("PROCESSING");
            job.setProgressPercent(80);
            uploadJobRepository.save(job);

            Bucket bucket = bucketRepository
                    .findByUserAndNameAndActiveTrue(user, bucketName)
                    .orElseThrow(() -> new RuntimeException(
                            "Bucket not found: " + bucketName));

            String resolvedKey = (objectKey != null
                    && !objectKey.isBlank())
                    ? sanitizeKey(objectKey)
                    : UUID.randomUUID() + "_"
                      + sanitizeKey(originalFilename);

            String finalS3Key = buildS3Key(
                    user.getTenantId(), bucketName, resolvedKey);


            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(rootBucket)
                    .sourceKey(tempKey)
                    .destinationBucket(rootBucket)
                    .destinationKey(finalS3Key)
                    .build());


            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(rootBucket)
                    .key(tempKey)
                    .build());

            log.info("Moved to final location: {}", finalS3Key);

            LifecyclePolicy policy = lifecyclePolicyRepository
                    .findByBucketAndActiveTrue(bucket)
                    .orElse(null);
            boolean versioningEnabled = policy != null
                    && policy.isVersioningEnabled();


            if (versioningEnabled) {
                String versionSuffix = UUID.randomUUID().toString()
                        .replace("-", "").substring(0, 8);
                String keyWithoutExt = resolvedKey.contains(".")
                        ? resolvedKey.substring(
                        0, resolvedKey.lastIndexOf("."))
                        : resolvedKey;
                String ext = resolvedKey.contains(".")
                        ? resolvedKey.substring(
                        resolvedKey.lastIndexOf("."))
                        : "";
                finalS3Key = user.getTenantId() + "/" + bucketName + "/"
                        + keyWithoutExt + "_v" + versionSuffix + ext;
            } else {
                finalS3Key = buildS3Key(
                        user.getTenantId(), bucketName, resolvedKey);
            }

            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(rootBucket)
                    .sourceKey(tempKey)
                    .destinationBucket(rootBucket)
                    .destinationKey(finalS3Key)
                    .build());


            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(rootBucket)
                    .key(tempKey)
                    .build());

            log.info("Moved to final location: {}", finalS3Key);



           
            StorageObject existingObj = storageObjectRepository
                    .findByBucketAndObjectKeyAndDeletedFalse(
                            bucket, resolvedKey)
                    .orElse(null);

            if (!versioningEnabled && existingObj != null) {
                existingObj.setDeleted(true);
                existingObj.setDeletedAt(LocalDateTime.now());
                storageObjectRepository.save(existingObj);
                bucket.setTotalSizeBytes(bucket.getTotalSizeBytes()
                        - existingObj.getSizeBytes());
                bucket.setObjectCount(bucket.getObjectCount() - 1);
                existingObj = null;
            }

            job.setProgressPercent(90);
            uploadJobRepository.save(job);

            String resolvedOriginalName = originalFilename;
            if (resolvedOriginalName == null
                    || resolvedOriginalName.isBlank()) {
                String[] parts = resolvedKey.split("/");
                resolvedOriginalName = parts[parts.length - 1];
            }

            StorageObject obj;
            if (versioningEnabled && existingObj != null) {
                existingObj.setSizeBytes(fileSize);
                existingObj.setCosKey(finalS3Key);
                obj = storageObjectRepository.save(existingObj);
            } else {
                obj = StorageObject.builder()
                        .objectKey(resolvedKey)
                        .originalFilename(resolvedOriginalName)
                        .contentType(resolvedContentType)
                        .sizeBytes(fileSize)
                        .cosKey(finalS3Key)
                        .bucket(bucket)
                        .user(user)
                        .deleted(false)
                        .build();
                obj = storageObjectRepository.save(obj);
                bucket.setObjectCount(bucket.getObjectCount() + 1);
                bucket.setTotalSizeBytes(
                        bucket.getTotalSizeBytes() + fileSize);
            }

            bucketRepository.save(bucket);


            if (versioningEnabled) {
                versionRepository
                        .findByStorageObjectOrderByVersionNumberDesc(obj)
                        .forEach(v -> {
                            if (v.isCurrent()) {
                                v.setCurrent(false);
                                versionRepository.save(v);
                            }
                        });

                long versionCount = versionRepository
                        .countByStorageObjectAndDeletedFalse(obj);

                ObjectVersion version = ObjectVersion.builder()
                        .storageObject(obj)
                        .bucket(bucket)
                        .versionNumber((int) versionCount + 1)
                        .sizeBytes(fileSize)
                        .cosKey(finalS3Key)
                        .isCurrent(true)
                        .currentTier(LifecycleTier.STANDARD)
                        .deleted(false)
                        .build();

                versionRepository.save(version);
            }

            meteringService.record(user, bucket, OperationType.PUT,
                    fileSize, 0, resolvedKey);

            job.setStatus("COMPLETED");
            job.setProgressPercent(100);
            job.setResultObjectKey(resolvedKey);
            job.setStorageClass(bucket.getStorageClass().name());
            job.setCompletedAt(LocalDateTime.now());
            uploadJobRepository.save(job);


            if (cacheManager != null) {
                try {
                    org.springframework.cache.Cache currentUsageCache =
                            cacheManager.getCache("currentUsage");
                    if (currentUsageCache != null) {
                        currentUsageCache.evict(user.getId());
                    }
                    org.springframework.cache.Cache bucketsCache =
                            cacheManager.getCache("buckets");
                    if (bucketsCache != null) {
                        bucketsCache.evict(user.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to evict caches for user {}: {}",
                            user.getId(), e.getMessage());
                }
            }

            log.info("Async upload completed: jobId={} objectKey={}",
                    job.getJobId(), resolvedKey);

        } catch (Exception e) {
            log.error("Async upload failed: jobId={} error={}",
                    job.getJobId(), e.getMessage());


            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(rootBucket)
                        .key(tempKey)
                        .build());
                log.info("Cleaned up temp file after failure: {}",
                        tempKey);
            } catch (Exception ignored) {}

            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            uploadJobRepository.save(job);
        }
    }

    private String buildS3Key(String tenantId, String bucketName,
                              String objectKey) {
        return tenantId + "/" + bucketName + "/" + objectKey;
    }

    private String sanitizeKey(String key) {
        if (key == null) return "unnamed";
        return key.replaceAll("\\.\\./", "")
                .replaceAll("\\./", "")
                .replaceAll("^/+", "")
                .replaceAll("[^a-zA-Z0-9._\\-/]", "_");
    }
}