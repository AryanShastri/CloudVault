package com.cloudvault.storage_engine.entity;

import com.cloudvault.storage_engine.enums.LifecycleTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;


@Entity
@Table(name = "object_versions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ObjectVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_object_id", nullable = false)
    private StorageObject storageObject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id", nullable = false)
    private Bucket bucket;

    @Column(nullable = false)
    private int versionNumber;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(length = 64)
    private String etag;

    @Column(nullable = false, length = 2048)
    private String cosKey;


    @Column(nullable = false)
    @Builder.Default
    private boolean isCurrent = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LifecycleTier currentTier = LifecycleTier.STANDARD;

    @Column
    private LocalDateTime tierChangedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;
}