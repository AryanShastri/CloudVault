package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.Bucket;
import com.cloudvault.storage_engine.entity.BucketLifecycleEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BucketLifecycleEventRepository
        extends JpaRepository<BucketLifecycleEvent, Long> {

    List<BucketLifecycleEvent> findByBucketOrderByTransitionedAtAsc(Bucket bucket);


    @Query("SELECT e FROM BucketLifecycleEvent e " +
            "WHERE e.bucket = :bucket " +
            "AND e.transitionedAt >= :monthStart " +
            "AND e.transitionedAt < :monthEnd " +
            "ORDER BY e.transitionedAt ASC")
    List<BucketLifecycleEvent> findByBucketAndMonth(
            @Param("bucket") Bucket bucket,
            @Param("monthStart") LocalDateTime monthStart,
            @Param("monthEnd") LocalDateTime monthEnd);
}