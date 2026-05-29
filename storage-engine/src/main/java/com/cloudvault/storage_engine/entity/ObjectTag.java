package com.cloudvault.storage_engine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "object_tags",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"storage_object_id", "tag_key"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ObjectTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_object_id", nullable = false)
    private StorageObject storageObject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 128)
    private String tagKey;

    @Column(nullable = false, length = 256)
    private String tagValue;

    @CreationTimestamp
    private LocalDateTime createdAt;
}