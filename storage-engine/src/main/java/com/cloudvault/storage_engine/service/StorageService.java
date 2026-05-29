package com.cloudvault.storage_engine.service;

import com.cloudvault.storage_engine.dto.ScanResult;
import com.cloudvault.storage_engine.dto.StorageDtos;
import com.cloudvault.storage_engine.dto.StorageDtos.CreateBucketRequest;
import com.cloudvault.storage_engine.dto.StorageDtos.BucketResponse;
import com.cloudvault.storage_engine.dto.StorageDtos.ObjectResponse;
import com.cloudvault.storage_engine.dto.StorageDtos.UploadResponse;
import com.cloudvault.storage_engine.dto.StorageDtos.PresignedUrlResponse;
import com.cloudvault.storage_engine.entity.*;
import com.cloudvault.storage_engine.enums.LifecycleTier;
import com.cloudvault.storage_engine.enums.OperationType;
import com.cloudvault.storage_engine.enums.PolicyType;
import com.cloudvault.storage_engine.enums.StorageClass;
import com.cloudvault.storage_engine.exception.ConflictException;
import com.cloudvault.storage_engine.exception.ResourceNotFoundException;
import com.cloudvault.storage_engine.repository.BucketRepository;
import com.cloudvault.storage_engine.repository.LifecyclePolicyRepository;
import com.cloudvault.storage_engine.repository.ObjectVersionRepository;
import com.cloudvault.storage_engine.repository.StorageObjectRepository;
import com.cloudvault.storage_engine.repository.UploadJobRepository;
 import com.cloudvault.storage_engine.service.AsyncUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final BucketRepository bucketRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final MeteringService meteringService;
    private final LifecycleService lifecycleService;
    private final LifecyclePolicyRepository lifecyclePolicyRepository;
    private final ObjectVersionRepository versionRepository;
    private final UploadJobRepository uploadJobRepository;
    private final AsyncUploadService asyncUploadService;
    private final VirusScanService virusScanService;


    public StorageService(S3Client s3Client,
                          S3Presigner s3Presigner,
                          BucketRepository bucketRepository,
                          StorageObjectRepository storageObjectRepository,
                          MeteringService meteringService,
                          @Lazy LifecycleService lifecycleService,
                          LifecyclePolicyRepository lifecyclePolicyRepository,
                          ObjectVersionRepository versionRepository,
                          UploadJobRepository uploadJobRepository,
                          @Lazy AsyncUploadService asyncUploadService,
                          VirusScanService virusScanService) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketRepository = bucketRepository;
        this.storageObjectRepository = storageObjectRepository;
        this.meteringService = meteringService;
        this.lifecycleService = lifecycleService;
        this.lifecyclePolicyRepository = lifecyclePolicyRepository;
        this.versionRepository = versionRepository;
        this.uploadJobRepository = uploadJobRepository;
        this.asyncUploadService = asyncUploadService;
        this.virusScanService = virusScanService;
    }


    @Value("${minio.bucket}")
    private String rootBucket;



    @Transactional
    @CacheEvict(value = {"buckets", "currentUsage"}, key = "#user.id")
    public BucketResponse createBucket(User user, CreateBucketRequest request) {
        if (bucketRepository.existsByUserAndNameAndActiveTrue(user, request.getName())) {
            throw new ConflictException("Bucket already exists: " + request.getName());
        }

        Bucket bucket = Bucket.builder()
                .name(request.getName())
                .description(request.getDescription())
                .storageClass(StorageClass.STANDARD)
                .currentTier(LifecycleTier.STANDARD)
                .policyType(PolicyType.PREDEFINED)
                .lastAccessedAt(LocalDateTime.now())
                .tierChangedAt(LocalDateTime.now())
                .periodStartAt(LocalDateTime.now())
                .requestsInPeriod(0)
                .user(user)
                .objectCount(0)
                .totalSizeBytes(0)
                .active(true)
                .build();

        bucket = bucketRepository.save(bucket);
        meteringService.record(user, bucket, OperationType.PUT, 0, 0, null);
        log.info("Bucket created: {}/{} — starting in STANDARD tier",
                user.getTenantId(), request.getName());
        return toBucketResponse(bucket);
    }


    @Cacheable(value = "buckets", key = "#user.id")
    public List<BucketResponse> listBuckets(User user) {
        meteringService.record(user, null, OperationType.LIST, 0, 0, null);
        return bucketRepository.findByUserAndActiveTrue(user)
                .stream()
                .map(this::toBucketResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = {"buckets", "currentUsage"}, key = "#user.id")
    public void deleteBucket(User user, String bucketName) {
        Bucket bucket = getBucketOrThrow(user, bucketName);
        if (bucket.getObjectCount() > 0) {
            throw new ConflictException(
                    "Cannot delete non-empty bucket. Delete all objects first.");
        }
        bucket.setActive(false);
        bucketRepository.save(bucket);
        meteringService.record(user, bucket, OperationType.DELETE, 0, 0, null);
    }



    @Transactional
    @CacheEvict(value = {"currentUsage", "buckets"}, key = "#user.id")
    public UploadResponse uploadObject(User user, String bucketName,
                                       MultipartFile file,
                                       String objectKey) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }

        Bucket bucket = getBucketOrThrow(user, bucketName);

        String resolvedKey = (objectKey != null && !objectKey.isBlank())
                ? sanitizeKey(objectKey)
                : UUID.randomUUID() + "_" + sanitizeKey(file.getOriginalFilename());


        LifecyclePolicy policy = lifecyclePolicyRepository
                .findByBucketAndActiveTrue(bucket)
                .orElse(null);
        boolean versioningEnabled = policy != null && policy.isVersioningEnabled();

        String s3Key;

        if (versioningEnabled) {

            String versionSuffix = UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 8);
            String keyWithoutExt = resolvedKey.contains(".")
                    ? resolvedKey.substring(0, resolvedKey.lastIndexOf("."))
                    : resolvedKey;
            String ext = resolvedKey.contains(".")
                    ? resolvedKey.substring(resolvedKey.lastIndexOf("."))
                    : "";
            s3Key = user.getTenantId() + "/" + bucketName + "/"
                    + keyWithoutExt + "_v" + versionSuffix + ext;
        } else {
            s3Key = buildS3Key(user.getTenantId(), bucketName, resolvedKey);
        }

        StorageObject existingObj = storageObjectRepository
                .findByBucketAndObjectKeyAndDeletedFalse(bucket, resolvedKey)
                .orElse(null);


        if (!versioningEnabled && existingObj != null) {
            existingObj.setDeleted(true);
            existingObj.setDeletedAt(LocalDateTime.now());
            storageObjectRepository.save(existingObj);
            bucket.setTotalSizeBytes(
                    bucket.getTotalSizeBytes() - existingObj.getSizeBytes());
            bucket.setObjectCount(bucket.getObjectCount() - 1);
            existingObj = null;
        }


        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        ScanResult scanResult = virusScanService.scanStream(
                file.getInputStream(),
                file.getOriginalFilename());

        if (!scanResult.isClean()) {
            log.warn("Upload blocked — virus: {} in file: {}",
                    scanResult.getVirusName(), file.getOriginalFilename());
            throw new ConflictException(
                    "File upload rejected: malware detected. " +
                            "Threat: " + scanResult.getVirusName());
        }

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(rootBucket)
                .key(s3Key)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();

        PutObjectResponse putResponse = s3Client.putObject(
                putRequest,
                RequestBody.fromInputStream(
                        file.getInputStream(), file.getSize()));  


        


        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            String[] parts = resolvedKey.split("/");
            originalName = parts[parts.length - 1];
        }


        StorageObject obj;
        if (versioningEnabled && existingObj != null) {

            existingObj.setEtag(putResponse.eTag());
            existingObj.setSizeBytes(file.getSize());
            existingObj.setCosKey(s3Key);
            obj = storageObjectRepository.save(existingObj);
        } else {

            obj = StorageObject.builder()
                    .objectKey(resolvedKey)
                    .originalFilename(originalName)
                    .contentType(contentType)
                    .sizeBytes(file.getSize())
                    .etag(putResponse.eTag())
                    .cosKey(s3Key)
                    .bucket(bucket)
                    .user(user)
                    .deleted(false)
                    .build();
            obj = storageObjectRepository.save(obj);


            bucket.setObjectCount(bucket.getObjectCount() + 1);
            bucket.setTotalSizeBytes(bucket.getTotalSizeBytes() + file.getSize());
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
                    .sizeBytes(file.getSize())
                    .etag(putResponse.eTag())
                    .cosKey(s3Key)
                    .isCurrent(true)
                    .currentTier(LifecycleTier.STANDARD)
                    .deleted(false)
                    .build();

            versionRepository.save(version);
            log.info("Version {} created for object: {}",
                    versionCount + 1, resolvedKey);
        }

        meteringService.record(user, bucket, OperationType.PUT,
                file.getSize(), 0, resolvedKey);

        UploadResponse response = new UploadResponse();
        response.setObjectKey(resolvedKey);
        response.setOriginalFilename(originalName);
        response.setSizeBytes(file.getSize());
        response.setSizeFormatted(formatBytes(file.getSize()));
        response.setEtag(putResponse.eTag());
        response.setBucketName(bucketName);
        response.setStorageClass(bucket.getStorageClass().name());
        response.setUploadedAt(LocalDateTime.now());
        return response;
    }


    @Transactional(readOnly = true)
    public byte[] downloadObjectVersion(User user,
                                        String bucketName,
                                        String objectKey,
                                        int versionNumber)
            throws IOException {

        Bucket bucket = bucketRepository
                .findByUserAndNameAndActiveTrue(user, bucketName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bucket not found: " + bucketName));


        if (LifecycleTier.DEEP_GLACIER.equals(
                bucket.getCurrentTier())) {
            boolean isRestored = lifecycleService
                    .isObjectRestored(bucket, objectKey);
            if (!isRestored) {
                throw new ConflictException(
                        "Bucket is in DEEP_GLACIER. " +
                                "Request a restore first.");
            }
        }

        ObjectVersion version = versionRepository
                .findByBucketAndObjectKeyAndVersion(
                        bucket, objectKey, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version " + versionNumber +
                                " not found for object: " + objectKey));

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(rootBucket)
                .key(version.getCosKey())
                .build();

        byte[] data = s3Client.getObjectAsBytes(getRequest)
                .asByteArray();

        boolean isNoncurrent = !version.isCurrent();

        
        meteringService.recordVersionDownload(
                user, bucket, version.getSizeBytes(),
                data.length, objectKey, isNoncurrent);

        log.info("Version {} downloaded for object: {} " +
                        "noncurrent={}",
                versionNumber, objectKey, isNoncurrent);

        return data;
    }

    @CacheEvict(value = "currentUsage", key = "#user.id")
    @Transactional(readOnly = true)
    public byte[] downloadObject(User user, String bucketName,
                                 String objectKey) throws IOException {


        Bucket bucket = bucketRepository
                .findByUserAndNameAndActiveTrue(user, bucketName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bucket not found: " + bucketName));

        StorageObject obj = storageObjectRepository
                .findByBucketAndObjectKeyAndDeletedFalse(bucket, objectKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Object not found: " + objectKey));


        if (LifecycleTier.DEEP_GLACIER.equals(bucket.getCurrentTier())) {
            boolean isRestored = lifecycleService.isObjectRestored(
                    bucket, objectKey);
            if (!isRestored) {
                throw new ConflictException(
                        "Object is in DEEP_GLACIER. Request a restore first via " +
                                "POST /api/lifecycle/buckets/" + bucketName + "/restore");
            }
        }

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(rootBucket)
                .key(obj.getCosKey())
                .build();

        byte[] data = s3Client.getObjectAsBytes(getRequest).asByteArray();

        meteringService.record(user, bucket, OperationType.GET,
                obj.getSizeBytes(), obj.getSizeBytes(), objectKey);

        return data;
    }

    @CacheEvict(value = "currentUsage", key = "#user.id")
    public PresignedUrlResponse generatePresignedUrl(User user, String bucketName,
                                                     String objectKey,
                                                     int expiryMinutes) {
        Bucket bucket = getBucketOrThrow(user, bucketName);
        StorageObject obj = storageObjectRepository
                .findByBucketAndObjectKeyAndDeletedFalse(bucket, objectKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Object not found: " + objectKey));


        if (LifecycleTier.DEEP_GLACIER.equals(bucket.getCurrentTier())) {
            boolean isRestored = lifecycleService.isObjectRestored(
                    bucket, objectKey);
            if (!isRestored) {
                throw new ConflictException(
                        "Object is in DEEP_GLACIER. " +
                                "Request a restore before generating a presigned URL.");
            }
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expiryMinutes))
                .getObjectRequest(r -> r.bucket(rootBucket).key(obj.getCosKey()))
                .build();

        String url = s3Presigner.presignGetObject(presignRequest).url().toString();
        meteringService.record(user, bucket, OperationType.GET, 0, 0, objectKey);
        return new PresignedUrlResponse(url, (long) expiryMinutes * 60);
    }

    @Transactional
    @CacheEvict(value = {"currentUsage", "buckets"}, key = "#user.id")
    public void deleteObject(User user, String bucketName, String objectKey) {
        Bucket bucket = getBucketOrThrow(user, bucketName);
        StorageObject obj = storageObjectRepository
                .findByBucketAndObjectKeyAndDeletedFalse(bucket, objectKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Object not found: " + objectKey));

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(rootBucket)
                .key(obj.getCosKey())
                .build());

        obj.setDeleted(true);
        obj.setDeletedAt(LocalDateTime.now());
        storageObjectRepository.save(obj);

        bucket.setObjectCount(bucket.getObjectCount() - 1);
        bucket.setTotalSizeBytes(bucket.getTotalSizeBytes() - obj.getSizeBytes());
        bucketRepository.save(bucket);

        meteringService.record(user, bucket, OperationType.DELETE,
                obj.getSizeBytes(), 0, objectKey);
    }

    public Page<ObjectResponse> listObjects(User user, String bucketName,
                                            Pageable pageable) {
        Bucket bucket = getBucketOrThrow(user, bucketName);
        meteringService.record(user, bucket, OperationType.LIST, 0, 0, null);
        return storageObjectRepository
                .findByBucketAndDeletedFalse(bucket, pageable)
                .map(this::toObjectResponse);
    }



    private Bucket getBucketOrThrow(User user, String bucketName) {
        return bucketRepository.findByUserAndNameAndActiveTrue(user, bucketName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bucket not found: " + bucketName));
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

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private BucketResponse toBucketResponse(Bucket bucket) {
        BucketResponse r = new BucketResponse();
        r.setId(bucket.getId());
        r.setName(bucket.getName());
        r.setDescription(bucket.getDescription());
        r.setCurrentTier(bucket.getCurrentTier() != null
                ? bucket.getCurrentTier().name() : "STANDARD");
        r.setPolicyType(bucket.getPolicyType() != null
                ? bucket.getPolicyType().name() : "PREDEFINED");
        r.setTierChangedAt(bucket.getTierChangedAt() != null
                ? bucket.getTierChangedAt().toString() : null);
        r.setObjectCount(bucket.getObjectCount());
        r.setTotalSizeBytes(bucket.getTotalSizeBytes());
        r.setTotalSizeFormatted(formatBytes(bucket.getTotalSizeBytes()));
        r.setCreatedAt(bucket.getCreatedAt());
        return r;
    }

    private ObjectResponse toObjectResponse(StorageObject obj) {
        ObjectResponse r = new ObjectResponse();
        r.setId(obj.getId());
        r.setObjectKey(obj.getObjectKey());
        r.setOriginalFilename(obj.getOriginalFilename());
        r.setContentType(obj.getContentType());
        r.setSizeBytes(obj.getSizeBytes());
        r.setSizeFormatted(formatBytes(obj.getSizeBytes()));
        r.setEtag(obj.getEtag());
        r.setCreatedAt(obj.getCreatedAt());
        return r;
    }

    public StorageDtos.UploadJobResponse startAsyncUpload(
            User user,
            String bucketName,
            byte[] fileBytes,
            String originalFilename,
            String contentType,
            long fileSize,
            String objectKey) {

        getBucketOrThrow(user, bucketName);

        String jobId = UUID.randomUUID().toString();
        UploadJob job = UploadJob.builder()
                .jobId(jobId)
                .user(user)
                .bucketName(bucketName)
                .objectKey(objectKey)
                .originalFilename(originalFilename)
                .fileSizeBytes(fileSize)
                .status("PENDING")
                .progressPercent(0)
                .build();

        uploadJobRepository.save(job);

        asyncUploadService.processUpload(
                job, user, bucketName,
                fileBytes, originalFilename,
                contentType, fileSize, objectKey);

        log.info("Async job created: jobId={} bucket={} size={}",
                jobId, bucketName, formatBytes(fileSize));

        return new StorageDtos.UploadJobResponse(
                jobId, "PENDING", 0,
                originalFilename,
                formatBytes(fileSize),
                null, null,
                job.getCreatedAt());
    }


    public StorageDtos.UploadJobResponse getUploadJobStatus(User user, String jobId) {
        UploadJob job = uploadJobRepository
                .findByJobIdAndUser(jobId, user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Upload job not found: " + jobId));

        return new StorageDtos.UploadJobResponse(
                job.getJobId(),
                job.getStatus(),
                job.getProgressPercent(),
                job.getOriginalFilename(),
                job.getFileSizeBytes() != null
                        ? formatBytes(job.getFileSizeBytes()) : null,
                job.getResultObjectKey(),
                job.getErrorMessage(),
                job.getCreatedAt()
        );
    }


    public List<StorageDtos.UploadJobResponse> getAllUploadJobs(User user) {
        return uploadJobRepository
                .findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(job -> new StorageDtos.UploadJobResponse(
                        job.getJobId(),
                        job.getStatus(),
                        job.getProgressPercent(),
                        job.getOriginalFilename(),
                        job.getFileSizeBytes() != null
                                ? formatBytes(job.getFileSizeBytes()) : null,
                        job.getResultObjectKey(),
                        job.getErrorMessage(),
                        job.getCreatedAt()))
                .collect(Collectors.toList());
    }
}