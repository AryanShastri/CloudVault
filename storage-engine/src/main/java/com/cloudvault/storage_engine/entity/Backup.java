package com.cloudvault.storage_engine.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "backups")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Backup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String backupId;

    @Column(nullable = false)
    private String timestamp;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String status; // SUCCESS, FAILED, RESTORE_INITIATED

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}