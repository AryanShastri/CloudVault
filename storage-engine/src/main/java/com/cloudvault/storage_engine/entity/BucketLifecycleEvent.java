package com.cloudvault.storage_engine.entity;

import com.cloudvault.storage_engine.enums.LifecycleTier;
import com.cloudvault.storage_engine.enums.TransitionReason;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "bucket_lifecycle_events", indexes = {
        @Index(name = "idx_lifecycle_bucket_time",
                columnList = "bucket_id, transitionedAt")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class BucketLifecycleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id", nullable = false)
    private Bucket bucket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LifecycleTier fromTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LifecycleTier toTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransitionReason reason;


    @Column(nullable = false)
    private int daysInPreviousTier;


    @Column(precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal earlyDeletionCharge = BigDecimal.ZERO;

    @CreationTimestamp
    private LocalDateTime transitionedAt;
}