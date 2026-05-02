package com.cloudvault.storage_engine.entity;

import com.cloudvault.storage_engine.enums.OperationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "usage_records", indexes = {
        @Index(name = "idx_usage_user_period",
                columnList = "user_id, billingYear, billingMonth")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id")
    private Bucket bucket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperationType operationType;

    @Column(nullable = false)
    private long bytes = 0;

    @Column(nullable = false)
    private long bandwidthBytes = 0;

    @Column(length = 1024)
    private String objectKey;

    @Column(nullable = false)
    private int billingYear;

    @Column(nullable = false)
    private int billingMonth;

    @CreationTimestamp
    private LocalDateTime recordedAt;
}