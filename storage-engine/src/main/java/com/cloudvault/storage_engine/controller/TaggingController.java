package com.cloudvault.storage_engine.controller;

import com.cloudvault.storage_engine.dto.StorageDtos.*;
import com.cloudvault.storage_engine.entity.User;
import com.cloudvault.storage_engine.repository.UserRepository;
import com.cloudvault.storage_engine.service.TaggingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class TaggingController {

    private final TaggingService taggingService;
    private final UserRepository userRepository;


    @PutMapping("/buckets/{bucketName}/objects/{objectKey}/tags")
    public ResponseEntity<TagResponse> putTag(
            Authentication auth,
            @PathVariable String bucketName,
            @PathVariable String objectKey,
            @Valid @RequestBody TagRequest request) {
        return ResponseEntity.ok(taggingService.putTag(
                getUser(auth), bucketName, objectKey, request));
    }


    @GetMapping("/buckets/{bucketName}/objects/{objectKey}/tags")
    public ResponseEntity<List<TagResponse>> getTags(
            Authentication auth,
            @PathVariable String bucketName,
            @PathVariable String objectKey) {
        return ResponseEntity.ok(taggingService.getTags(
                getUser(auth), bucketName, objectKey));
    }


    @DeleteMapping("/buckets/{bucketName}/objects/{objectKey}/tags/{tagKey}")
    public ResponseEntity<Void> deleteTag(
            Authentication auth,
            @PathVariable String bucketName,
            @PathVariable String objectKey,
            @PathVariable String tagKey) {
        taggingService.deleteTag(getUser(auth), bucketName, objectKey, tagKey);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/objects/filter")
    public ResponseEntity<List<ObjectWithTagsResponse>> filterByTag(
            Authentication auth,
            @RequestParam String tagKey,
            @RequestParam(required = false) String tagValue) {
        return ResponseEntity.ok(taggingService.filterByTag(
                getUser(auth), tagKey, tagValue));
    }

    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}