package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.Backup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BackupRepository extends JpaRepository<Backup, Long> {

    Optional<Backup> findByBackupId(String backupId);

    List<Backup> findAllByOrderByCreatedAtDesc();

    void deleteByCreatedAtBefore(LocalDateTime date);
}