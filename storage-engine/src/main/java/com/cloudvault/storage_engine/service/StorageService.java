package com.cloudvault.storage_engine.service;

// Your DTO imports — explicit, no wildcard
import com.cloudvault.storage_engine.dto.StorageDtos.CreateBucketRequest;
import com.cloudvault.storage_engine.dto.StorageDtos.BucketResponse;
import com.cloudvault.storage_engine.dto.StorageDtos.ObjectResponse;
import com.cloudvault.storage_engine.dto.StorageDtos.UploadResponse;
import com.cloudvault.storage_engine.dto.StorageDtos.PresignedUrlResponse;

// Your entity imports
import com.cloudvault.storage_engine.entity.Bucket;
import com.cloudvault.storage_engine.entity.StorageObject;
import com.cloudvault.storage_engine.entity.User;

// Your other imports
import com.cloudvault.storage_engine.enums.OperationType;
import com.cloudvault.storage_engine.exception.ConflictException;
import com.cloudvault.storage_engine.exception.ResourceNotFoundException;
import com.cloudvault.storage_engine.repository.BucketRepository;
import com.cloudvault.storage_engine.repository.StorageObjectRepository;

// AWS SDK imports — explicit
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

// Spring imports
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// Java imports
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final BucketRepository bucketRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final MeteringService meteringService;

    @Value("${minio.bucket}")
    private String rootBucket;

    // ── BUCKET OPERATIONS ──────────────────────────────────────────────

    @Transactional
    public BucketResponse createBucket(User user, CreateBucketRequest request) {
        if (bucketRepository.existsByUserAndNameAndActiveTrue(user, request.getName())) {
            throw new ConflictException("Bucket already exists: " + request.getName());
        }

        Bucket bucket = Bucket.builder()
                .name(request.getName())
                .description(request.getDescription())
                .storageClass(request.getStorageClass() != null
                        ? request.getStorageClass()
                        : com.cloudvault.storage_engine.enums.StorageClass.STANDARD)
                .user(user)
                .objectCount(0)
                .totalSizeBytes(0)
                .active(true)
                .build();

        bucket = bucketRepository.save(bucket);
        meteringService.record(user, bucket, OperationType.PUT, 0, 0, null);
        log.info("Bucket created: {}/{}", user.getTenantId(), request.getName());
        return toBucketResponse(bucket);
    }

    public List<BucketResponse> listBuckets(User user) {
        meteringService.record(user, null, OperationType.LIST, 0, 0, null);
        return bucketRepository.findByUserAndActiveTrue(user)
                .stream()
                .map(this::toBucketResponse)
                .collect(Collectors.toList());
    }

    @Transactional
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

    // ── OBJECT OPERATIONS ──────────────────────────────────────────────

    @Transactional
    public UploadResponse uploadObject(User user, String bucketName,
                                       MultipartFile file,
                                       String objectKey) throws IOException {
        if (file == null ) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        Bucket bucket = getBucketOrThrow(user, bucketName);

        String resolvedKey = (objectKey != null && !objectKey.isBlank())
                ? sanitizeKey(objectKey)
                : UUID.randomUUID() + "_" + sanitizeKey(file.getOriginalFilename());

        String s3Key = buildS3Key(user.getTenantId(), bucketName, resolvedKey);

        // If object with same key exists, soft delete old one
        storageObjectRepository
                .findByBucketAndObjectKeyAndDeletedFalse(bucket, resolvedKey)
                .ifPresent(existing -> {
                    existing.setDeleted(true);
                    existing.setDeletedAt(LocalDateTime.now());
                    storageObjectRepository.save(existing);
                    bucket.setTotalSizeBytes(
                            bucket.getTotalSizeBytes() - existing.getSizeBytes());
                    bucket.setObjectCount(bucket.getObjectCount() - 1);
                });


        // Resolve content type — default to octet-stream if null
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

// Upload to MinIO
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(rootBucket)
                .key(s3Key)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();

        PutObjectResponse putResponse = s3Client.putObject(
                putRequest,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

// Save metadata to DB
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            String[] parts = resolvedKey.split("/");
            originalName = parts[parts.length - 1];
        }
        StorageObject obj = StorageObject.builder()
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

        storageObjectRepository.save(obj);

        // Update bucket stats
        bucket.setObjectCount(bucket.getObjectCount() + 1);
        bucket.setTotalSizeBytes(bucket.getTotalSizeBytes() + file.getSize());
        bucketRepository.save(bucket);

        // Meter the operation
        meteringService.record(user, bucket, OperationType.PUT,
                file.getSize(), 0, resolvedKey);

        UploadResponse response = new UploadResponse();
        response.setObjectKey(resolvedKey);
        response.setOriginalFilename(file.getOriginalFilename());
        response.setSizeBytes(file.getSize());
        response.setSizeFormatted(formatBytes(file.getSize()));
        response.setEtag(putResponse.eTag());
        response.setBucketName(bucketName);
        response.setStorageClass(bucket.getStorageClass().name());
        response.setUploadedAt(LocalDateTime.now());
        return response;
    }

    public byte[] downloadObject(User user, String bucketName,
                                 String objectKey) throws IOException {
        Bucket bucket = getBucketOrThrow(user, bucketName);
        StorageObject obj = storageObjectRepository
                .findByBucketAndObjectKeyAndDeletedFalse(bucket, objectKey)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Object not found: " + objectKey));

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(rootBucket)
                .key(obj.getCosKey())
                .build();

        byte[] data = s3Client.getObjectAsBytes(getRequest).asByteArray();

        meteringService.record(user, bucket, OperationType.GET,
                obj.getSizeBytes(), obj.getSizeBytes(), objectKey);

        return data;
    }

    public PresignedUrlResponse generatePresignedUrl(User user, String bucketName,
                                                     String objectKey,
                                                     int expiryMinutes) {
        Bucket bucket = getBucketOrThrow(user, bucketName);
        StorageObject obj = storageObjectRepository
                .findByBucketAndObjectKeyAndDeletedFalse(bucket, objectKey)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Object not found: " + objectKey));

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expiryMinutes))
                .getObjectRequest(r -> r.bucket(rootBucket).key(obj.getCosKey()))
                .build();

        String url = s3Presigner.presignGetObject(presignRequest).url().toString();
        meteringService.record(user, bucket, OperationType.GET, 0, 0, objectKey);
        return new PresignedUrlResponse(url, (long) expiryMinutes * 60);
    }

    @Transactional
    public void deleteObject(User user, String bucketName, String objectKey) {
        Bucket bucket = getBucketOrThrow(user, bucketName);
        StorageObject obj = storageObjectRepository
                .findByBucketAndObjectKeyAndDeletedFalse(bucket, objectKey)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Object not found: " + objectKey));

        // Delete from MinIO
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(rootBucket)
                .key(obj.getCosKey())
                .build());

        // Soft delete in DB
        obj.setDeleted(true);
        obj.setDeletedAt(LocalDateTime.now());
        storageObjectRepository.save(obj);

        // Update bucket stats
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

    // ── HELPERS ────────────────────────────────────────────────────────

    private Bucket getBucketOrThrow(User user, String bucketName) {
        return bucketRepository.findByUserAndNameAndActiveTrue(user, bucketName)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Bucket not found: " + bucketName));
    }

    private String buildS3Key(String tenantId, String bucketName, String objectKey) {
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
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private BucketResponse toBucketResponse(Bucket bucket) {
        BucketResponse r = new BucketResponse();
        r.setId(bucket.getId());
        r.setName(bucket.getName());
        r.setDescription(bucket.getDescription());
        r.setStorageClass(bucket.getStorageClass().name());
        r.setStorageClassLabel(storageClassLabel(bucket.getStorageClass()));
        r.setObjectCount(bucket.getObjectCount());
        r.setTotalSizeBytes(bucket.getTotalSizeBytes());
        r.setTotalSizeFormatted(formatBytes(bucket.getTotalSizeBytes()));
        r.setCreatedAt(bucket.getCreatedAt());
        return r;
    }

    private String storageClassLabel(
            com.cloudvault.storage_engine.enums.StorageClass sc) {
        return switch (sc) {
            case STANDARD -> "Standard";
            case VAULT -> "Vault";
            case COLD_VAULT -> "Cold Vault";
            case SMART_TIER -> "Smart Tier";
            case ARCHIVE -> "Archive";
        };
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
}