package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.Bucket;
import com.cloudvault.storage_engine.entity.UsageRecord;
import com.cloudvault.storage_engine.entity.User;
import com.cloudvault.storage_engine.enums.OperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {

    @Query("SELECT COUNT(u) FROM UsageRecord u WHERE u.user = :user " +
            "AND u.billingYear = :year AND u.billingMonth = :month " +
            "AND u.operationType = :opType")
    long countByUserAndPeriodAndType(
            @Param("user") User user,
            @Param("year") int year,
            @Param("month") int month,
            @Param("opType") OperationType opType);

    @Query("SELECT COUNT(u) FROM UsageRecord u WHERE u.user = :user " +
            "AND u.bucket = :bucket " +
            "AND u.billingYear = :year AND u.billingMonth = :month " +
            "AND u.operationType = :opType")
    long countByUserAndBucketAndPeriodAndType(
            @Param("user") User user,
            @Param("bucket") Bucket bucket,
            @Param("year") int year,
            @Param("month") int month,
            @Param("opType") OperationType opType);

    @Query("SELECT COALESCE(SUM(u.bandwidthBytes), 0) FROM UsageRecord u " +
            "WHERE u.user = :user " +
            "AND u.billingYear = :year AND u.billingMonth = :month")
    long sumBandwidthByUserAndPeriod(
            @Param("user") User user,
            @Param("year") int year,
            @Param("month") int month);

    @Query("SELECT COALESCE(SUM(u.bandwidthBytes), 0) FROM UsageRecord u " +
            "WHERE u.user = :user AND u.bucket = :bucket " +
            "AND u.billingYear = :year AND u.billingMonth = :month")
    long sumBandwidthByUserAndBucketAndPeriod(
            @Param("user") User user,
            @Param("bucket") Bucket bucket,
            @Param("year") int year,
            @Param("month") int month);
}