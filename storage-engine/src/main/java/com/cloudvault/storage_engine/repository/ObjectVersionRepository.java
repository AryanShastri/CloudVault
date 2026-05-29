package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.Bucket;
import com.cloudvault.storage_engine.entity.ObjectVersion;
import com.cloudvault.storage_engine.entity.StorageObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ObjectVersionRepository
        extends JpaRepository<ObjectVersion, Long> {

    List<ObjectVersion> findByStorageObjectOrderByVersionNumberDesc(
            StorageObject storageObject);

    Optional<ObjectVersion> findByStorageObjectAndIsCurrentTrue(
            StorageObject storageObject);

   

    long countByStorageObjectAndDeletedFalse(StorageObject storageObject);

    @Query("SELECT COALESCE(SUM(v.sizeBytes), 0) " +
            "FROM ObjectVersion v " +
            "WHERE v.bucket = :bucket " +
            "AND v.deleted = false " +
            "AND v.isCurrent = false")
    long sumNoncurrentVersionSizeByBucket(@Param("bucket") Bucket bucket);

    @Query("SELECT v FROM ObjectVersion v " +
            "WHERE v.bucket = :bucket " +
            "AND v.isCurrent = false " +
            "AND v.deleted = false")
    List<ObjectVersion> findByBucketAndIsCurrentFalseAndDeletedFalse(
            @Param("bucket") Bucket bucket);

    @Query("SELECT v FROM ObjectVersion v " +
            "WHERE v.storageObject.bucket = :bucket " +
            "AND v.storageObject.objectKey = :objectKey " +
            "AND v.deleted = false " +
            "ORDER BY v.versionNumber DESC")
    List<ObjectVersion> findVersionsByBucketAndObjectKey(
            @Param("bucket") Bucket bucket,
            @Param("objectKey") String objectKey);

    @Query("SELECT v FROM ObjectVersion v " +
            "WHERE v.storageObject.bucket = :bucket " +
            "AND v.storageObject.objectKey = :objectKey " +
            "AND v.versionNumber = :versionNumber " +
            "AND v.deleted = false")
    Optional<ObjectVersion> findByBucketAndObjectKeyAndVersion(
            @Param("bucket") Bucket bucket,
            @Param("objectKey") String objectKey,
            @Param("versionNumber") int versionNumber);

    
}