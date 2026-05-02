package com.cloudvault.storage_engine.dto;

import com.cloudvault.storage_engine.enums.InvoiceStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class BillingDtos {

    @Data
    public static class UsageSummary {
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
        private BigDecimal estimatedRequestCharge;
        private BigDecimal estimatedBandwidthCharge;
        private BigDecimal estimatedTotal;
    }

    @Data
    public static class BucketItemResponse {
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
    }

    @Data
    public static class InvoiceResponse {
        private Long id;
        private int billingYear;
        private int billingMonth;
        private String billingPeriod;

        // Aggregate totals
        private long storageBytesUsed;
        private String storageFormatted;
        private long classARequests;
        private long classBRequests;
        private long freeRequests;
        private long bandwidthBytesOut;
        private String bandwidthFormatted;

        private BigDecimal totalCharge;
        private BigDecimal amountDue;

        // Per bucket breakdown
        private List<BucketItemResponse> bucketItems;

        private InvoiceStatus status;
        private LocalDateTime generatedAt;
        private LocalDateTime paidAt;
    }

    @Data
    public static class AdminOverview {
        private long totalUsers;
        private long totalStorageBytes;
        private String totalStorageFormatted;
        private BigDecimal totalRevenueThisMonth;
        private BigDecimal totalRevenueAllTime;
    }
}