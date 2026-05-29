package com.cloudvault.storage_engine.dto;

import com.cloudvault.storage_engine.enums.InvoiceStatus;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class BillingDtos {

    @Data
    public static class UsageSummary implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        
        private String tenantId;
        private String username;
        private int year;
        private int month;

        private long storageBytesUsed;
        private String storageFormatted;
        private double storageGb;

        private long classARequests;
        private long classBRequests;
        private long freeRequests;

        private long bandwidthBytesOut;
        private String bandwidthFormatted;
        private double bandwidthGb;

        private BigDecimal estimatedStorageCharge;
        private BigDecimal estimatedClassACharge;
        private BigDecimal estimatedClassBCharge;
        private BigDecimal estimatedRequestCharge;
        private BigDecimal estimatedBandwidthCharge;
        private BigDecimal estimatedTotal;

        private List<BucketItemResponse> bucketItems;
        private BigDecimal estimatedVersioningCharge = BigDecimal.ZERO;
    }

    @Data
    public static class BucketItemResponse implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String bucketName;
        private String storageClass;
        private long storageBytesUsed;
        private String storageFormatted;
        private long classARequests;
        private long classBRequests;
        private long bandwidthBytesOut;
        private String bandwidthFormatted;
        private BigDecimal storageCharge;
        private BigDecimal classACharge;
        private BigDecimal classBCharge;
        private BigDecimal bandwidthCharge;
        private BigDecimal retrievalCharge;
        private BigDecimal subtotal;
        private long currentVersionBytes;
        private String currentVersionFormatted;
        private long noncurrentVersionBytes;
        private String noncurrentVersionFormatted;
        private BigDecimal versioningStorageCharge;
        private boolean versioningEnabled;
        
    }

    @Data
    public static class InvoiceResponse  implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long id;
        private int billingYear;
        private int billingMonth;
        private String billingPeriod;


        private long storageBytesUsed;
        private String storageFormatted;
        private long classARequests;
        private long classBRequests;
        private long freeRequests;
        private long bandwidthBytesOut;
        private String bandwidthFormatted;

        private BigDecimal totalCharge;
        private BigDecimal amountDue;


        private List<BucketItemResponse> bucketItems;

        private InvoiceStatus status;
        private LocalDateTime generatedAt;
        private LocalDateTime paidAt;
    }

    @Data
    public static class PricingLine implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String category;
        private String name;
        private String unit;
        private String rate;
        private String notes;
    }

    @Data
    public static class PricingReference implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private List<PricingLine> lines;
    }

    @Data
    public static class AdminOverview {
        private long totalUsers;
        private long totalStorageBytes;
        private String totalStorageFormatted;
        private BigDecimal totalRevenueThisMonth;
        private BigDecimal totalRevenueAllTime;
    }

    @Data
    public static class AuditLogResponse {
        private Long id;
        private String operationType;
        private String requestClass;
        private String bucketName;
        private String objectKey;
        private String sizeFormatted;
        private String bandwidthFormatted;
        private String tierAtTimeOfRequest;
        private String recordedAt;


        private String timestamp;
        private long sizeBytes;
        private String tierAtTime;
    }
}