package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.Bucket;
import com.cloudvault.storage_engine.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BucketRepository extends JpaRepository<Bucket, Long> {

    List<Bucket> findByUserAndActiveTrue(User user);

    Optional<Bucket> findByUserAndNameAndActiveTrue(User user, String name);

    boolean existsByUserAndNameAndActiveTrue(User user, String name);

    @Query("SELECT COALESCE(SUM(b.totalSizeBytes), 0) FROM Bucket b WHERE b.user = :user AND b.active = true")
    Long sumTotalSizeByUser(@Param("user") User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bucket b WHERE b.id = :id")
    Optional<Bucket> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(v.sizeBytes), 0) " +
            "FROM ObjectVersion v " +
            "WHERE v.bucket = :bucket " +
            "AND v.deleted = false " +
            "AND v.isCurrent = false")
    long sumNoncurrentVersionSizeByBucket(@Param("bucket") Bucket bucket);

    @Query("SELECT COALESCE(SUM(v.sizeBytes), 0) " +
            "FROM ObjectVersion v " +
            "WHERE v.bucket = :bucket " +
            "AND v.deleted = false")
    long sumAllVersionsSizeByBucket(@Param("bucket") Bucket bucket);
}