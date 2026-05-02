package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.Bucket;
import com.cloudvault.storage_engine.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}