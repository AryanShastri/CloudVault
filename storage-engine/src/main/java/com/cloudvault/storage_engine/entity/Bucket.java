package com.cloudvault.storage_engine.entity;

import com.cloudvault.storage_engine.enums.LifecycleTier;
import com.cloudvault.storage_engine.enums.PolicyType;
import com.cloudvault.storage_engine.enums.StorageClass;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "buckets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Bucket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 63)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StorageClass storageClass = StorageClass.STANDARD;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private long objectCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private long totalSizeBytes = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LifecycleTier currentTier = LifecycleTier.STANDARD;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PolicyType policyType = PolicyType.PREDEFINED;


    @Column
    private LocalDateTime tierChangedAt;

    
    @Column
    private LocalDateTime lastAccessedAt;


    @Column(nullable = false)
    @Builder.Default
    private int requestsInPeriod = 0;


    @Column
    private LocalDateTime periodStartAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "bucket", cascade = CascadeType.ALL,
            fetch = FetchType.LAZY)
    private List<StorageObject> objects;
}