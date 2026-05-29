package com.cloudvault.storage_engine.entity;

import com.cloudvault.storage_engine.enums.RestoreSpeed;
import com.cloudvault.storage_engine.enums.RestoreStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "restore_requests")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RestoreRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id", nullable = false)
    private Bucket bucket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    @Column(length = 1024)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestoreSpeed restoreSpeed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RestoreStatus status = RestoreStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime requestedAt;

    @Column
    private LocalDateTime estimatedCompletion;

    @Column
    private LocalDateTime completedAt;

    @Column
    private LocalDateTime accessExpiresAt;

    @Column(nullable = false)
    @Builder.Default
    private long restoredBytes = 0;

    @Column(precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal restoreFeeCharged = BigDecimal.ZERO;
}