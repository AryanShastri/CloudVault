package com.cloudvault.storage_engine.entity;

import com.cloudvault.storage_engine.enums.LifecycleTier;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "lifecycle_transition_rules")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LifecycleTransitionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private LifecyclePolicy policy;


    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LifecycleTier fromTier;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LifecycleTier toTier;

    @Column
    private Integer daysOfInactivity;

    
}