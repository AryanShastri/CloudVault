package com.cloudvault.storage_engine.controller;

import com.cloudvault.storage_engine.enums.UserRole;
import com.cloudvault.storage_engine.repository.UserRepository;
import com.cloudvault.storage_engine.service.BackupService;
import com.cloudvault.storage_engine.service.BackupService.BackupResult;
import com.cloudvault.storage_engine.service.BackupService.BackupResponse;
import com.cloudvault.storage_engine.service.BackupService.BackupRestoreResponse;
import com.cloudvault.storage_engine.entity.User;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/backups")
@RequiredArgsConstructor
@Slf4j
public class BackupController {

    private final BackupService backupService;
    private final UserRepository userRepository;


    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> backupHealth() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "BackupService");
        response.put("timestamp", new java.util.Date().toString());
        return ResponseEntity.ok(response);
    }


    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerBackup(
            Authentication auth) {

        User user = getUser(auth);
        validateAdmin(user);

        log.info("Backup triggered by admin: {}", user.getUsername());

        try {
            BackupResult result = backupService.createFullBackup();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("backupId", result.getBackupId());
            response.put("status", result.getStatus());
            response.put("location", result.getLocation());
            response.put("size", result.getSize());
            response.put("formattedSize", formatBytes(result.getSize()));
            response.put("createdAt", result.getCreatedAt());
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Backup trigger failed: {}", e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "FAILED");
            error.put("error", e.getMessage());
            error.put("timestamp", new Date());
            return ResponseEntity.internalServerError().body(error);
        }
    }


    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listBackups(
            Authentication auth) {

        User user = getUser(auth);
        validateAdmin(user);

        log.info("Backup list requested by admin: {}", user.getUsername());

        try {
            List<BackupResponse> backups = backupService.getAllBackups();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("backups", backups);
            response.put("totalBackups", backups.size());
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to list backups: {}", e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "ERROR");
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }


    @PostMapping("/restore/{backupId}")
    public ResponseEntity<Map<String, Object>> restoreBackup(
            Authentication auth,
            @PathVariable String backupId) {

        User user = getUser(auth);
        validateAdmin(user);

        log.warn("RESTORE initiated by admin: {} — backupId: {}",
                user.getUsername(), backupId);

        try {
            BackupRestoreResponse restoreResponse =
                    backupService.initiateRestore(backupId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("backupId", restoreResponse.getBackupId());
            response.put("status", restoreResponse.getStatus());
            response.put("message", restoreResponse.getMessage());
            response.put("databaseFile", restoreResponse.getDatabaseFile());
            response.put("restoreSteps", restoreResponse.getRestoreSteps());
            response.put("warning", "RESTORE IS MANUAL — Follow steps in restoreSteps");
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Restore initiation failed: {}", e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "ERROR");
            error.put("error", e.getMessage());
            error.put("backupId", backupId);
            return ResponseEntity.internalServerError().body(error);
        }
    }


    @GetMapping("/info/{backupId}")
    public ResponseEntity<Map<String, Object>> getBackupInfo(
            Authentication auth,
            @PathVariable String backupId) {

        User user = getUser(auth);
        validateAdmin(user);

        try {
            List<BackupResponse> backups = backupService.getAllBackups();
            BackupResponse backup = backups.stream()
                    .filter(b -> b.getBackupId().equals(backupId))
                    .findFirst()
                    .orElseThrow(() ->
                            new RuntimeException("Backup not found: " + backupId));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("backupId", backup.getBackupId());
            response.put("timestamp", backup.getTimestamp());
            response.put("size", backup.getSize());
            response.put("formattedSize", backup.getFormattedSize());
            response.put("status", backup.getStatus());
            response.put("createdAt", backup.getCreatedAt());
            response.put("location", backup.getLocation());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get backup info: {}", e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "ERROR");
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }



    private User getUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof com.cloudvault.storage_engine.entity.User) {
            return (com.cloudvault.storage_engine.entity.User) principal;
        }

        throw new AccessDeniedException("Invalid principal type");
    }

    private void validateAdmin(User user) {
        if (user == null) {
            throw new AccessDeniedException("Not authenticated");
        }


        UserRole role = user.getRole();
        if (role == null || role != UserRole.ADMIN) {
            throw new AccessDeniedException(
                    "Admin access required — user role: " + role);
        }

    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}