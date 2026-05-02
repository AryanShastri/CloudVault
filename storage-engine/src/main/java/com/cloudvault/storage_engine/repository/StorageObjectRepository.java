package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.Bucket;
import com.cloudvault.storage_engine.entity.StorageObject;
import com.cloudvault.storage_engine.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StorageObjectRepository extends JpaRepository<StorageObject, Long> {

    Page<StorageObject> findByBucketAndDeletedFalse(Bucket bucket, Pageable pageable);

    Optional<StorageObject> findByBucketAndObjectKeyAndDeletedFalse(Bucket bucket, String objectKey);

    boolean existsByBucketAndObjectKeyAndDeletedFalse(Bucket bucket, String objectKey);

    long countByBucketAndDeletedFalse(Bucket bucket);
}