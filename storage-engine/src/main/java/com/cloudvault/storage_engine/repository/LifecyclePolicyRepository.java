package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.Bucket;
import com.cloudvault.storage_engine.entity.LifecyclePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LifecyclePolicyRepository
        extends JpaRepository<LifecyclePolicy, Long> {

    Optional<LifecyclePolicy> findByBucketAndActiveTrue(Bucket bucket);

    boolean existsByBucket(Bucket bucket);
}