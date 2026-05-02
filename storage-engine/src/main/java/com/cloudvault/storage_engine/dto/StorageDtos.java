package com.cloudvault.storage_engine.dto;

import com.cloudvault.storage_engine.enums.StorageClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

public class StorageDtos {

    @Data
    public static class CreateBucketRequest {
        @NotBlank(message = "Bucket name is required")
        @Size(min = 3, max = 63, message = "Bucket name must be 3-63 characters")
        @Pattern(regexp = "^[a-z0-9][a-z0-9\\-]*[a-z0-9]$",
                message = "Bucket name must be lowercase letters, numbers and hyphens only")
        private String name;

        @Size(max = 255)
        private String description;

        private StorageClass storageClass = StorageClass.STANDARD;
    }

    @Data
    public static class BucketResponse {
        private Long id;
        private String name;
        private String description;
        private String storageClass;
        private String storageClassLabel;
        private long objectCount;
        private long totalSizeBytes;
        private String totalSizeFormatted;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ObjectResponse {
        private Long id;
        private String objectKey;
        private String originalFilename;
        private String contentType;
        private long sizeBytes;
        private String sizeFormatted;
        private String etag;
        private LocalDateTime createdAt;
    }

    @Data
    public static class UploadResponse {
        private String objectKey;
        private String originalFilename;
        private long sizeBytes;
        private String sizeFormatted;
        private String etag;
        private String bucketName;
        private String storageClass;
        private LocalDateTime uploadedAt;
    }

    @Data
    public static class PresignedUrlResponse {
        private String url;
        private long expiresInSeconds;

        public PresignedUrlResponse(String url, long expiresInSeconds) {
            this.url = url;
            this.expiresInSeconds = expiresInSeconds;
        }
    }
}