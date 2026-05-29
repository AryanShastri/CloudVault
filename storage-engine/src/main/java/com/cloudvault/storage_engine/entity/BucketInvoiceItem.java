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


        @Column(length = 30)
        private String lifecycleTier;

         @Column(nullable = false)
         @Builder.Default
         private long currentVersionBytes = 0;

         @Column(nullable = false)
            @Builder.Default
            private long noncurrentVersionBytes = 0;

          @Column(nullable = false, precision = 12, scale = 6)private BigDecimal versioningStorageCharge = BigDecimal.ZERO;


        private long storageBytesUsed;
        private long classARequests;
        private long classBRequests;
        private long bandwidthBytesOut;


        @Builder.Default
        private BigDecimal storageCharge = BigDecimal.ZERO;
        private BigDecimal classACharge = BigDecimal.ZERO;
        @Builder.Default
        private BigDecimal classBCharge = BigDecimal.ZERO;
        private BigDecimal bandwidthCharge = BigDecimal.ZERO;
        @Builder.Default
        private BigDecimal retrievalCharge = BigDecimal.ZERO;
        private BigDecimal subtotal = BigDecimal.ZERO;
    }

