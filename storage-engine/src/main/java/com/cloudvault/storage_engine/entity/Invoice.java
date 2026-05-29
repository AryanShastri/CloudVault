package com.cloudvault.storage_engine.entity;

import com.cloudvault.storage_engine.enums.InvoiceStatus;
import com.cloudvault.storage_engine.enums.StorageClass;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "billingYear", "billingMonth", }))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int billingYear;

    @Column(nullable = false)
    private int billingMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StorageClass storageClass;

    @Column(nullable = false)
    private long storageBytesUsed;

    @Column(nullable = false)
    private long billableBytesUsed;

    @Column(nullable = false)
    private long classARequests;

    @Column(nullable = false)
    private long classBRequests;

    @Column(nullable = false)
    private long freeRequests;

    @Column(nullable = false)
    private long bandwidthBytesOut;

    @Column(nullable = false)
    private long archiveRestoreBytes;

    @Column(length = 10)
    private String smartTierClassification;

    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal storageCapacityCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal classARequestCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal classBRequestCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal bandwidthCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal dataRetrievalCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal archiveRestoreCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal minDurationCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal totalCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal amountDue = BigDecimal.ZERO;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Builder.Default
    private List<BucketInvoiceItem> bucketItems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.GENERATED;

    @CreationTimestamp
    private LocalDateTime generatedAt;

    private LocalDateTime paidAt;
}