package com.cloudvault.storage_engine.service;

import com.cloudvault.storage_engine.entity.Backup;
import com.cloudvault.storage_engine.repository.BackupRepository;
import com.cloudvault.storage_engine.repository.BucketRepository;
import com.cloudvault.storage_engine.repository.StorageObjectRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final S3Client s3Client;
    private final BucketRepository bucketRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final BackupRepository backupRepository;

    @Value("${minio.bucket}")
    private String rootBucket;

    @Value("${backup.local-path:/var/backups/cloudvault}")
    private String backupLocalPath;

    @Value("${backup.retention-days:30}")
    private int retentionDays;

    @Value("${database.name:storage_billing}")
    private String databaseName;

    @Value("${database.user:root}")
    private String dbUser;

    @Value("${database.password:}")
    private String dbPassword;

    @Value("${database.host:localhost}")
    private String dbHost;

    @Value("${database.port:3306}")
    private int dbPort;

    @Value("${backup.bucket:backup-storage}")
    private String backupBucket;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;



    public void dailyBackup() {
        log.info("═══════════════════════════════════════════════════════");
        log.info("Starting daily backup...");
        log.info("═══════════════════════════════════════════════════════");
        try {
            BackupResult result = createFullBackup();
            log.info("Daily backup COMPLETED: backupId={} size={}MB",
                    result.getBackupId(),
                    result.getSize() / (1024 * 1024));
        } catch (Exception e) {
            log.error("Daily backup FAILED: {}", e.getMessage(), e);
            sendBackupAlert("FAILED", e.getMessage());
        }
    }


    @Transactional
    public BackupResult createFullBackup() {
        String backupId = UUID.randomUUID().toString();
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String backupDirName = timestamp + "_" + backupId;
        String backupDir = backupLocalPath + "/" + backupDirName;

        try {

            new File(backupDir).mkdirs();
            log.info("Backup directory created: {}", backupDir);


            String sqlFile = backupDir + "/database.sql";
            log.info("Step 1/5: Backing up MySQL database...");
            dumpDatabase(sqlFile);
            log.info("✓ MySQL backup created: {}", sqlFile);


            String objectsFile = backupDir + "/objects_metadata.csv";
            log.info("Step 2/5: Exporting object metadata...");
            exportObjectMetadata(objectsFile);
            log.info("✓ Object metadata exported: {}", objectsFile);


            String bucketsFile = backupDir + "/buckets_metadata.csv";
            log.info("Step 3/5: Exporting bucket metadata...");
            exportBucketMetadata(bucketsFile);
            log.info("✓ Bucket metadata exported: {}", bucketsFile);


            String minioBackupDir = backupDir + "/minio-files";
            new File(minioBackupDir).mkdirs();
            log.info("Step 4/5: Backing up ALL MinIO files...");
            backupMinIOFiles(minioBackupDir);
            log.info("✓ MinIO files backed up: {}", minioBackupDir);

            log.info("Step 5/5: Uploading to MinIO backup bucket...");
            uploadToMinIO(backupDir, backupId, timestamp, backupDirName);
            log.info("✓ Backup uploaded to MinIO bucket: {}", backupBucket);


            long backupSize = getFolderSize(new File(backupDir));
            saveBackupRecord(backupId, timestamp, backupDir, backupSize, "SUCCESS");

            try {
                deleteDirectory(new File(backupDir));
                log.info("✓ Local backup cleaned up: {}", backupDir);
            } catch (Exception e) {
                log.warn("Could not delete local backup: {}", e.getMessage());
            }

            cleanupOldBackups();

            BackupResult result = new BackupResult();
            result.setBackupId(backupId);
            result.setStatus("SUCCESS");
            result.setLocation(backupDir);
            result.setSize(backupSize);
            result.setCreatedAt(LocalDateTime.now());

            log.info("═══════════════════════════════════════════════════════");
            log.info("BACKUP COMPLETE: {} — {} MB",
                    backupId, backupSize / (1024 * 1024));
            log.info("═══════════════════════════════════════════════════════");

            return result;

        } catch (Exception e) {
            log.error("Backup creation FAILED: {}", e.getMessage());
            saveBackupRecord(backupId, timestamp, backupDir, 0, "FAILED: " + e.getMessage());
            throw new RuntimeException("Backup creation failed", e);
        }
    }


    private void backupMinIOFiles(String backupDir) throws Exception {
        log.info("Copying MinIO files directly to backup bucket...");

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(rootBucket)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        List<S3Object> objects = response.contents();

        int totalFiles = 0;
        long totalBytes = 0;

        if (objects != null && !objects.isEmpty()) {
            for (S3Object obj : objects) {
                String key = obj.key();

                if (key.startsWith("temp-scan/")) continue;


                String backupKey = "backups/" + backupDir
                        .substring(backupDir.lastIndexOf("/") + 1)
                        + "/minio-files/" + key;

                s3Client.copyObject(CopyObjectRequest.builder()
                        .sourceBucket(rootBucket)
                        .sourceKey(key)
                        .destinationBucket(rootBucket)
                        .destinationKey(backupKey)
                        .build());


                totalFiles++;
                totalBytes += obj.size();

                if (totalFiles % 10 == 0) {
                    log.info("  {} files copied... ({}MB so far)",
                            totalFiles, totalBytes / (1024 * 1024));
                }
            }

            log.info("MinIO backup complete: {} files, {}MB",
                    totalFiles, totalBytes / (1024 * 1024));
        }
    }


    private void downloadMinIOFile(String s3Key, String backupDir)
            throws Exception {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(rootBucket)
                    .key(s3Key)
                    .build();

            String localPath = backupDir + "/" + s3Key;
            File localFile = new File(localPath);
            localFile.getParentFile().mkdirs();

            try (var response = s3Client.getObject(request)) {
                Files.copy(
                        response,
                        localFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            log.debug("Downloaded: {} → {}", s3Key, localPath);

        } catch (NoSuchKeyException e) {
            log.warn("Key not found: {}", s3Key);
        }
    }


    private void uploadToMinIO(String backupDir,
                               String backupId,
                               String timestamp,
                               String backupDirName) {

        File backupFolder = new File(backupDir);
        File[] files = backupFolder.listFiles();

        if (files == null || files.length == 0) {
            log.warn("No files found in backup dir: {}", backupDir);
            return;
        }

        for (File file : files) {

            if (file.isDirectory()) continue;
            

            try {
                // Upload database.sql and CSVs to MinIO backup bucket
                String s3Key = "backups/" + backupDirName
                        + "/" + file.getName();

                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(rootBucket)
                                .key(s3Key)
                                .contentLength(file.length())
                                .build(),
                        RequestBody.fromFile(file));

                log.info("✓ Uploaded to MinIO: {} ({})",
                        file.getName(),
                        formatBytes(file.length()));

            } catch (Exception e) {
                log.error("Failed to upload {} to MinIO: {}",
                        file.getName(), e.getMessage());
            }
        }

        log.info("All backup files uploaded to MinIO: backups/{}/",
                backupDirName);
    }

    private void uploadFilesRecursive(File[] files,
                                      String basePath,
                                      String minioPrefix) {
        for (File file : files) {
            if (file.isDirectory()) {
                uploadFilesRecursive(file.listFiles(), basePath, minioPrefix);
            } else {
                try {
                    String relativePath = file.getAbsolutePath()
                            .substring(basePath.length() + 1)
                            .replace("\\", "/");
                    String s3Key = minioPrefix + "/" + relativePath;

                    s3Client.putObject(
                            PutObjectRequest.builder()
                                    .bucket(rootBucket)
                                    .key(s3Key)
                                    .contentLength(file.length())
                                    .build(),
                            RequestBody.fromFile(file));

                    log.debug("Uploaded: {} → {}", file.getName(), s3Key);

                } catch (Exception e) {
                    log.error("Failed to upload file: {} — {}",
                            file.getName(), e.getMessage());
                }
            }
        }
    }


    private void exportObjectMetadata(String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write("id,bucketName,objectKey,originalFilename," +
                    "contentType,sizeBytes,etag,createdAt,deleted\n");

            // ── FIXED: Use JOIN FETCH to avoid lazy loading ─────────────
            storageObjectRepository.findAllWithBucket().forEach(obj -> {
                try {
                    writer.write(String.format(
                            "%d,%s,%s,%s,%s,%d,%s,%s,%b\n",
                            obj.getId(),
                            obj.getBucket().getName(),
                            obj.getObjectKey(),
                            escapeCSV(obj.getOriginalFilename()),
                            obj.getContentType(),
                            obj.getSizeBytes(),
                            obj.getEtag(),
                            obj.getCreatedAt(),
                            obj.isDeleted()));
                } catch (IOException e) {
                    log.error("Error writing object metadata: {}", e.getMessage());
                }
            });
            // ── END FIX ────────────────────────────────────────────────
        }
    }


    private void exportBucketMetadata(String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write("id,bucketName,description,storageClass," +
                    "currentTier,objectCount,totalSizeBytes,createdAt\n");


            bucketRepository.findAll().forEach(bucket -> {
                try {
                    writer.write(String.format(
                            "%d,%s,%s,%s,%s,%d,%d,%s\n",
                            bucket.getId(),
                            bucket.getName(),
                            escapeCSV(bucket.getDescription()),
                            bucket.getStorageClass() != null
                                    ? bucket.getStorageClass().name() : "",
                            bucket.getCurrentTier() != null
                                    ? bucket.getCurrentTier().name() : "",
                            bucket.getObjectCount(),
                            bucket.getTotalSizeBytes(),
                            bucket.getCreatedAt()));
                } catch (IOException e) {
                    log.error("Error writing bucket metadata: {}", e.getMessage());
                }
            });

        }
    }


    private void dumpDatabase(String outputFile) throws Exception {
        String command = String.format(
                "mysqldump -h%s -P%d -u%s -p%s %s",
                dbHost, dbPort, dbUser, dbPassword, databaseName);

        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
        pb.redirectOutput(new File(outputFile));
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException(
                    "mysqldump failed with exit code: " + exitCode);
        }
    }


    private void saveBackupRecord(String backupId, String timestamp,
                                  String location, long size, String status) {
        try {
            Backup backup = Backup.builder()
                    .backupId(backupId)
                    .timestamp(timestamp)
                    .location(location)
                    .sizeBytes(size)
                    .status(status)
                    .createdAt(LocalDateTime.now())
                    .build();

            backupRepository.save(backup);

        } catch (Exception e) {
            log.error("Failed to save backup record: {}", e.getMessage());
        }
    }


    private void cleanupOldBackups() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now()
                    .minusDays(retentionDays);

            backupRepository.deleteByCreatedAtBefore(cutoffDate);

            File backupDir = new File(backupLocalPath);
            if (backupDir.exists()) {
                File[] folders = backupDir.listFiles(File::isDirectory);
                if (folders != null) {
                    for (File folder : folders) {
                        LocalDateTime folderTime = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(folder.lastModified()),
                                ZoneId.systemDefault());

                        if (folderTime.isBefore(cutoffDate)) {
                            deleteDirectory(folder);
                            log.info("Deleted old backup: {}", folder.getName());
                        }
                    }
                }
            }

            log.info("Backup cleanup completed");

        } catch (Exception e) {
            log.error("Backup cleanup failed: {}", e.getMessage());
        }
    }


    public List<BackupResponse> getAllBackups() {
        try {
            return backupRepository.findAllByOrderByCreatedAtDesc()
                    .stream()
                    .map(b -> {
                        BackupResponse r = new BackupResponse();
                        r.setBackupId(b.getBackupId());
                        r.setTimestamp(b.getTimestamp());
                        r.setSize(b.getSizeBytes());
                        r.setFormattedSize(formatBytes(b.getSizeBytes()));
                        r.setStatus(b.getStatus());
                        r.setCreatedAt(b.getCreatedAt());
                        r.setLocation(b.getLocation());
                        return r;
                    })
                    .toList();
        } catch (Exception e) {
            log.error("Failed to retrieve backups: {}", e.getMessage());
            return new ArrayList<>();
        }
    }


    public BackupRestoreResponse initiateRestore(String backupId) {
        Backup backup = backupRepository.findByBackupId(backupId)
                .orElseThrow(() ->
                        new RuntimeException("Backup not found: " + backupId));

        log.warn("╔══════════════════════════════════════════════════════════╗");
        log.warn("║ BACKUP RESTORE INITIATED                                 ║");
        log.warn("║ backupId: {}                              ║", backupId);
        log.warn("║ Location: {}                      ║", backup.getLocation());
        log.warn("╚══════════════════════════════════════════════════════════╝");

        String restoreSteps = String.format(
                "MANUAL RESTORE STEPS:\n" +
                        "1. Stop CloudVault application\n" +
                        "2. Restore MySQL database:\n" +
                        "   mysql -u root -p storage_billing < %s/database.sql\n" +
                        "3. Restore MinIO files from backup:\n" +
                        "   Backup location: %s/minio-files/\n" +
                        "4. Start CloudVault application\n" +
                        "5. Verify by checking: GET /api/storage/buckets",
                backup.getLocation(),
                backup.getLocation()
        );

        BackupRestoreResponse response = new BackupRestoreResponse();
        response.setBackupId(backupId);
        response.setStatus("RESTORE_INITIATED");
        response.setMessage("Restore is manual. See logs for steps.");
        response.setDatabaseFile(backup.getLocation() + "/database.sql");
        response.setMinIOFilesDir(backup.getLocation() + "/minio-files");
        response.setRestoreSteps(restoreSteps);

        return response;
    }



    private long getFolderSize(File folder) {
        long size = 0;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else {
                    size += getFolderSize(file);
                }
            }
        }
        return size;
    }

    private void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException(
                    "Failed to delete directory: " + directory.getPath());
        }
    }

    private String escapeCSV(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void sendBackupAlert(String status, String message) {
        log.error("║ BACKUP ALERT ║ {} — {}", status, message);
    }



    public static class BackupResult {
        public String backupId;
        public String status;
        public String location;
        public long size;
        public LocalDateTime createdAt;

        public String getBackupId() { return backupId; }
        public void setBackupId(String id) { this.backupId = id; }
        public String getStatus() { return status; }
        public void setStatus(String s) { this.status = s; }
        public String getLocation() { return location; }
        public void setLocation(String l) { this.location = l; }
        public long getSize() { return size; }
        public void setSize(long s) { this.size = s; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime dt) { this.createdAt = dt; }
    }

    public static class BackupResponse {
        public String backupId;
        public String timestamp;
        public long size;
        public String formattedSize;
        public String status;
        public LocalDateTime createdAt;
        public String location;

        public String getBackupId() { return backupId; }
        public void setBackupId(String id) { this.backupId = id; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String t) { this.timestamp = t; }
        public long getSize() { return size; }
        public void setSize(long s) { this.size = s; }
        public String getFormattedSize() { return formattedSize; }
        public void setFormattedSize(String f) { this.formattedSize = f; }
        public String getStatus() { return status; }
        public void setStatus(String s) { this.status = s; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime dt) { this.createdAt = dt; }
        public String getLocation() { return location; }
        public void setLocation(String l) { this.location = l; }
    }

    public static class BackupRestoreResponse {
        public String backupId;
        public String status;
        public String message;
        public String databaseFile;
        public String minIOFilesDir;
        public String restoreSteps;

        public String getBackupId() { return backupId; }
        public void setBackupId(String id) { this.backupId = id; }
        public String getStatus() { return status; }
        public void setStatus(String s) { this.status = s; }
        public String getMessage() { return message; }
        public void setMessage(String m) { this.message = m; }
        public String getDatabaseFile() { return databaseFile; }
        public void setDatabaseFile(String f) { this.databaseFile = f; }
        public String getMinIOFilesDir() { return minIOFilesDir; }
        public void setMinIOFilesDir(String d) { this.minIOFilesDir = d; }
        public String getRestoreSteps() { return restoreSteps; }
        public void setRestoreSteps(String s) { this.restoreSteps = s; }
    }
}