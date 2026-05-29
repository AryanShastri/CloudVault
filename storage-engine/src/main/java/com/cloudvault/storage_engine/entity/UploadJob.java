package com.cloudvault.storage_engine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "upload_jobs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UploadJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String jobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 63)
    private String bucketName;

    @Column(length = 1024)
    private String objectKey;

    @Column(length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";
    // PENDING    → job created, waiting to start
    //UPLOADING  → uploading to temp MinIO location
    //SCANNING   → ClamAV scanning from temp location
    //PROCESSING → moving to final location, saving to DB
    //COMPLETED  → done, file accessible
    //REJECTED   → virus found, file deleted
    //FAILED     → unexpected error

    @Column(nullable = false)
    @Builder.Default
    private int progressPercent = 0;

    @Column
    private Long fileSizeBytes;

    @Column(length = 1024)
    private String errorMessage;

    @Column(length = 1024)
    private String resultObjectKey;

    @Column(length = 20)
    private String storageClass;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime completedAt;
}