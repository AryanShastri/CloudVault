package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.Bucket;
import com.cloudvault.storage_engine.entity.RestoreRequest;
import com.cloudvault.storage_engine.entity.User;
import com.cloudvault.storage_engine.enums.RestoreStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestoreRequestRepository
        extends JpaRepository<RestoreRequest, Long> {

    List<RestoreRequest> findByStatus(RestoreStatus status);

    List<RestoreRequest> findByUserOrderByRequestedAtDesc(User user);

    Optional<RestoreRequest> findByBucketAndObjectKeyAndStatus(
            Bucket bucket, String objectKey, RestoreStatus status);

    List<RestoreRequest> findByBucketAndStatus(
            Bucket bucket, RestoreStatus status);
}