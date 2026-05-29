package com.cloudvault.storage_engine.entity;

import com.cloudvault.storage_engine.enums.PolicyType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "lifecycle_policies")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LifecyclePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id", nullable = false, unique = true)
    private Bucket bucket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PolicyType policyType = PolicyType.PREDEFINED;


    @OneToMany(mappedBy = "policy",
            cascade = CascadeType.ALL,
            fetch = FetchType.EAGER,
            orphanRemoval = true)
    @Builder.Default
    private List<LifecycleTransitionRule> transitionRules = new ArrayList<>();


    @Column
    private Integer expirationDays;


    @Column(nullable = false)
    @Builder.Default
    private boolean versioningEnabled = false;


    @Column
    private Integer maxVersionsToKeep;


    @Column
    private Integer deleteNoncurrentAfterDays;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}