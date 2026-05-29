package com.cloudvault.storage_engine.controller;

import com.cloudvault.storage_engine.dto.ScanResult;
import com.cloudvault.storage_engine.dto.StorageDtos;
import com.cloudvault.storage_engine.dto.StorageDtos.*;
import com.cloudvault.storage_engine.entity.User;
import com.cloudvault.storage_engine.repository.UserRepository;
import com.cloudvault.storage_engine.service.StorageService;
import com.cloudvault.storage_engine.service.VirusScanService;
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
    private static final long ASYNC_THRESHOLD_BYTES = 100L * 1024 * 1024; // 100MB
    private final StorageService storageService;
    private final UserRepository userRepository;
    private final VirusScanService virusScanService;



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



    @PostMapping("/buckets/{bucketName}/objects")
    public ResponseEntity<?> uploadObject(
            Authentication auth,
            @PathVariable String bucketName,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String key)
            throws IOException {

        User user = getUser(auth);
        long fileSize = file.getSize();
        long ASYNC_THRESHOLD = 100L * 1024 * 1024; // 100MB

        if (fileSize > ASYNC_THRESHOLD) {

            byte[] fileBytes = file.getBytes();
            StorageDtos.UploadJobResponse job =
                    storageService.startAsyncUpload(
                            user, bucketName, fileBytes,
                            file.getOriginalFilename(),
                            file.getContentType(),
                            fileSize, key);
            return ResponseEntity.accepted().body(job);
        } else {

            UploadResponse response =
                    storageService.uploadObject(
                            user, bucketName, file, key);
            return ResponseEntity.ok(response);
        }
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

    @GetMapping("/buckets/{bucketName}/objects/{*remainingPath}")
    public ResponseEntity<?> handleObjectGetOperations(
            Authentication auth,
            @PathVariable String bucketName,
            @PathVariable String remainingPath,
            @RequestParam(defaultValue = "60") int expiryMinutes) throws IOException {

        String path = remainingPath.startsWith("/") ? remainingPath.substring(1) : remainingPath;
        path = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);

        if (path.endsWith("/download")) {
            String objectKey = path.substring(0, path.length() - "/download".length());
            User user = getUser(auth);
            byte[] data = storageService.downloadObject(user, bucketName, objectKey);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + objectKey + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(data.length)
                    .body(data);
        } else if (path.endsWith("/presign")) {
            String objectKey = path.substring(0, path.length() - "/presign".length());
            User user = getUser(auth);
            return ResponseEntity.ok(storageService.generatePresignedUrl(
                    user, bucketName, objectKey, expiryMinutes));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/buckets/{bucketName}/objects/{*objectKey}")
    public ResponseEntity<Void> deleteObject(
            Authentication auth,
            @PathVariable String bucketName,
            @PathVariable String objectKey) {
        String key = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        key = java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8);
        storageService.deleteObject(getUser(auth), bucketName, key);
        return ResponseEntity.noContent().build();
    }



    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }


    

    @GetMapping("/upload-jobs/{jobId}")
    public ResponseEntity<StorageDtos.UploadJobResponse> getUploadJobStatus(
            Authentication auth,
            @PathVariable String jobId) {
        User user = getUser(auth);
        return ResponseEntity.ok(
                storageService.getUploadJobStatus(user, jobId));
    }


    @GetMapping("/upload-jobs")
    public ResponseEntity<List<StorageDtos.UploadJobResponse>> getAllUploadJobs(
            Authentication auth) {
        User user = getUser(auth);
        return ResponseEntity.ok(storageService.getAllUploadJobs(user));
    }

    @GetMapping("/buckets/{bucketName}/objects/{objectKey}/download")
    public ResponseEntity<byte[]> downloadObject(
            Authentication auth,
            @PathVariable String bucketName,
            @PathVariable String objectKey) throws IOException {

        User user = getUser(auth);
        byte[] data = storageService.downloadObject(
                user, bucketName, objectKey);

       

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", objectKey);
        

        headers.add("X-Scan-Status", "CLEAN");
        headers.add("Access-Control-Expose-Headers", "X-Scan-Status");
        

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }
}