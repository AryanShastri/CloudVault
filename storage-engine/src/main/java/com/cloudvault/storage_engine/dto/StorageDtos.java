package com.cloudvault.storage_engine.dto;

import com.cloudvault.storage_engine.enums.PolicyType;
import com.cloudvault.storage_engine.enums.StorageClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

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

       
    }


    @Data
    public static class BucketResponse implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long id;
        private String name;
        private String description;
   
        //  lifecycle tier info
        private String currentTier;
        private String policyType;
        private String tierChangedAt;
        private int daysUntilNextDowngrade;

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
    // LIFECYCLE DTOs

    @Data
    public static class CreateLifecyclePolicyRequest {

        private PolicyType policyType = PolicyType.PREDEFINED;

        /**
         * Only required when policyType = CUSTOM
         * Each rule defines one transition
         */
        private List<TransitionRuleRequest> transitionRules;

        private Integer expirationDays;
        private boolean versioningEnabled = false;
        private Integer maxVersionsToKeep;
        private Integer deleteNoncurrentAfterDays;

        @Data
        public static class TransitionRuleRequest {
            private String fromTier;
            private String toTier;
            private Integer daysOfInactivity;
           
        }
    }

    @Data
    public static class LifecycleStatusResponse {
        private String bucketName;
        private String currentTier;
        private String policyType;
        private String tierChangedAt;
        private String lastAccessedAt;
        private int daysInCurrentTier;
        private int requestsInPeriod;
        private String nextDowngradeAt;
        private String nextTierIfDowngraded;
        private int requestsUntilUpgrade;
        private String upgradesTo;
        private boolean versioningEnabled;
        private List<LifecycleEventResponse> recentEvents;

        @Data
        public static class LifecycleEventResponse {
            private String fromTier;
            private String toTier;
            private String reason;
            private String transitionedAt;
            private int daysInPreviousTier;
        }
    }

    @Data
    public static class RestoreRequestDto {
        private String objectKey;
        private String restoreSpeed; // EXPEDITED, STANDARD, BULK
    }

    @Data
    public static class RestoreStatusResponse {
        private Long id;
        private String objectKey;
        private String restoreSpeed;
        private String status;
        private String requestedAt;
        private String estimatedCompletion;
        private String completedAt;
        private String accessExpiresAt;
        private double restoreFeeCharged;
        private String message;
    }

    @Data
    public static class TagRequest {
        @NotBlank
        @Size(max = 128)
        private String key;

        @NotBlank
        @Size(max = 256)
        private String value;
    }

    @Data
    public static class TagResponse {
        private String key;
        private String value;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ObjectWithTagsResponse {
        private String objectKey;
        private String originalFilename;
        private String sizeFormatted;
        private String contentType;
        private String currentTier;
        private List<TagResponse> tags;
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UploadJobResponse {
        private String jobId;
        private String status;        // PENDING, PROCESSING, COMPLETED, FAILED
        private int progressPercent;
        private String originalFilename;
        private String fileSizeFormatted;
        private String resultObjectKey; // set when COMPLETED
        private String errorMessage;    // set when FAILED
        private LocalDateTime createdAt;
    }

    @Data
    public static class VersionResponse {
        private int versionNumber;
        private long sizeBytes;
        private String sizeFormatted;
        private boolean isCurrent;
        private String currentTier;
        private LocalDateTime createdAt;
        private String storageChargePerGB;
        private String bandwidthNote;
    }
}