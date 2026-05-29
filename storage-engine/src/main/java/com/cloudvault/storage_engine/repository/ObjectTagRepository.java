package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.ObjectTag;
import com.cloudvault.storage_engine.entity.StorageObject;
import com.cloudvault.storage_engine.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ObjectTagRepository extends JpaRepository<ObjectTag, Long> {

    List<ObjectTag> findByStorageObject(StorageObject storageObject);

    Optional<ObjectTag> findByStorageObjectAndTagKey(
            StorageObject storageObject, String tagKey);

    void deleteByStorageObjectAndTagKey(
            StorageObject storageObject, String tagKey);


    @Query("SELECT t FROM ObjectTag t WHERE t.user = :user " +
            "AND t.tagKey = :key AND t.tagValue = :value")
    List<ObjectTag> findByUserAndTagKeyAndTagValue(
            @Param("user") User user,
            @Param("key") String key,
            @Param("value") String value);


    @Query("SELECT t FROM ObjectTag t WHERE t.user = :user " +
            "AND t.tagKey = :key")
    List<ObjectTag> findByUserAndTagKey(
            @Param("user") User user,
            @Param("key") String key);
}