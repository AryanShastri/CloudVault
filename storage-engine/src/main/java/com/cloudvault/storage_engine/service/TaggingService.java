package com.cloudvault.storage_engine.service;

import com.cloudvault.storage_engine.dto.StorageDtos.*;
import com.cloudvault.storage_engine.entity.*;
import com.cloudvault.storage_engine.exception.ResourceNotFoundException;
import com.cloudvault.storage_engine.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaggingService {

    private final ObjectTagRepository tagRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final BucketRepository bucketRepository;


    @Transactional
    public TagResponse putTag(User user, String bucketName,
                              String objectKey, TagRequest request) {
        StorageObject obj = getObject(user, bucketName, objectKey);


        ObjectTag tag = tagRepository
                .findByStorageObjectAndTagKey(obj, request.getKey())
                .orElse(ObjectTag.builder()
                        .storageObject(obj)
                        .user(user)
                        .tagKey(request.getKey())
                        .build());

        tag.setTagValue(request.getValue());
        tag = tagRepository.save(tag);

        log.debug("Tag set: object={} key={} value={}",
                objectKey, request.getKey(), request.getValue());

        return toTagResponse(tag);
    }



    public List<TagResponse> getTags(User user, String bucketName,
                                     String objectKey) {
        StorageObject obj = getObject(user, bucketName, objectKey);
        return tagRepository.findByStorageObject(obj)
                .stream()
                .map(this::toTagResponse)
                .collect(Collectors.toList());
    }


    @Transactional
    public void deleteTag(User user, String bucketName,
                          String objectKey, String tagKey) {
        StorageObject obj = getObject(user, bucketName, objectKey);
        tagRepository.deleteByStorageObjectAndTagKey(obj, tagKey);
        log.debug("Tag deleted: object={} key={}", objectKey, tagKey);
    }

    public List<ObjectWithTagsResponse> filterByTag(
            User user, String tagKey, String tagValue) {
        List<ObjectTag> tags = tagValue != null
                ? tagRepository.findByUserAndTagKeyAndTagValue(
                user, tagKey, tagValue)
                : tagRepository.findByUserAndTagKey(user, tagKey);

        return tags.stream()
                .map(t -> {
                    StorageObject obj = t.getStorageObject();
                    ObjectWithTagsResponse r = new ObjectWithTagsResponse();
                    r.setObjectKey(obj.getObjectKey());
                    r.setOriginalFilename(obj.getOriginalFilename());
                    r.setSizeFormatted(StorageService.formatBytes(obj.getSizeBytes()));
                    r.setContentType(obj.getContentType());
                    r.setTags(tagRepository.findByStorageObject(obj)
                            .stream()
                            .map(this::toTagResponse)
                            .collect(Collectors.toList()));
                    return r;
                })
                .collect(Collectors.toList());
    }



    private StorageObject getObject(User user, String bucketName,
                                    String objectKey) {
        Bucket bucket = bucketRepository
                .findByUserAndNameAndActiveTrue(user, bucketName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bucket not found: " + bucketName));

        return storageObjectRepository
                .findByBucketAndObjectKeyAndDeletedFalse(bucket, objectKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Object not found: " + objectKey));
    }

    private TagResponse toTagResponse(ObjectTag tag) {
        TagResponse r = new TagResponse();
        r.setKey(tag.getTagKey());
        r.setValue(tag.getTagValue());
        r.setCreatedAt(tag.getCreatedAt());
        return r;
    }
}