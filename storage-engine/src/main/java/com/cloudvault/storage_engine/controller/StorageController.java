package com.cloudvault.storage_engine.controller;

import com.cloudvault.storage_engine.dto.StorageDtos.*;
import com.cloudvault.storage_engine.entity.User;
import com.cloudvault.storage_engine.repository.UserRepository;
import com.cloudvault.storage_engine.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.cloudvault.storage_engine.dto.StorageDtos.*;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final UserRepository userRepository;

    // ── BUCKET ENDPOINTS ───────────────────────────────────────────────

    @PostMapping("/buckets")
    public ResponseEntity<BucketResponse> createBucket(
            Authentication auth,
            @RequestBody CreateBucketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storageService.createBucket(getUser(auth), request));
    }

    @GetMapping("/buckets")
    public ResponseEntity<List<BucketResponse>> listBuckets(Authentication auth) {
        return ResponseEntity.ok(storageService.listBuckets(getUser(auth)));
    }

    @DeleteMapping("/buckets/{bucketName}")
    public ResponseEntity<Void> deleteBucket(
            Authentication auth,
            @PathVariable String bucketName) {
        storageService.deleteBucket(getUser(auth), bucketName);
        return ResponseEntity.noContent().build();
    }

    // ── OBJECT ENDPOINTS ───────────────────────────────────────────────

    @PostMapping("/buckets/{bucketName}/objects")
    public ResponseEntity<UploadResponse> uploadObject(
            Authentication auth,
            @PathVariable String bucketName,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "key", required = false) String objectKey)
            throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storageService.uploadObject(
                        getUser(auth), bucketName, file, objectKey));
    }

    @GetMapping("/buckets/{bucketName}/objects")
    public ResponseEntity<Page<ObjectResponse>> listObjects(
            Authentication auth,
            @PathVariable String bucketName,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                storageService.listObjects(getUser(auth), bucketName, pageable));
    }

    @GetMapping("/buckets/{bucketName}/objects/{objectKey}/download")
    public ResponseEntity<byte[]> downloadObject(
            Authentication auth,
            @PathVariable String bucketName,
            @PathVariable String objectKey) throws IOException {
        User user = getUser(auth);
        byte[] data = storageService.downloadObject(user, bucketName, objectKey);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + objectKey + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(data);
    }

    @GetMapping("/buckets/{bucketName}/objects/{objectKey}/presign")
    public ResponseEntity<PresignedUrlResponse> presignObject(
            Authentication auth,
            @PathVariable String bucketName,
            @PathVariable String objectKey,
            @RequestParam(defaultValue = "60") int expiryMinutes) {
        return ResponseEntity.ok(storageService.generatePresignedUrl(
                getUser(auth), bucketName, objectKey, expiryMinutes));
    }

    @DeleteMapping("/buckets/{bucketName}/objects/{objectKey}")
    public ResponseEntity<Void> deleteObject(
            Authentication auth,
            @PathVariable String bucketName,
            @PathVariable String objectKey) {
        storageService.deleteObject(getUser(auth), bucketName, objectKey);
        return ResponseEntity.noContent().build();
    }

    // ── HELPER ─────────────────────────────────────────────────────────

    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}