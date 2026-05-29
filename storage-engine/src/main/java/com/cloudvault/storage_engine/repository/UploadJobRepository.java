package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.UploadJob;
import com.cloudvault.storage_engine.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UploadJobRepository extends JpaRepository<UploadJob, Long> {

    Optional<UploadJob> findByJobId(String jobId);

    List<UploadJob> findByUserOrderByCreatedAtDesc(User user);

    Optional<UploadJob> findByJobIdAndUser(String jobId, User user);
}