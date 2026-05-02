package com.cloudvault.storage_engine.entity;

import com.cloudvault.storage_engine.enums.StorageClass;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;


@Entity
    @Table(name = "bucket_invoice_items")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public class BucketInvoiceItem {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "invoice_id", nullable = false)
        private Invoice invoice;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "bucket_id", nullable = false)
        private Bucket bucket;

        private String bucketName;

        @Enumerated(EnumType.STRING)
        private StorageClass storageClass;

        // Usage
        private long storageBytesUsed;
        private long classARequests;
        private long classBRequests;
        private long bandwidthBytesOut;

        // Charges
        private BigDecimal storageCharge = BigDecimal.ZERO;
        private BigDecimal classACharge = BigDecimal.ZERO;
        private BigDecimal classBCharge = BigDecimal.ZERO;
        private BigDecimal bandwidthCharge = BigDecimal.ZERO;
        private BigDecimal retrievalCharge = BigDecimal.ZERO;
        private BigDecimal subtotal = BigDecimal.ZERO;
    }

