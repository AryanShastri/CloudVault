package com.cloudvault.storage_engine.controller;

import com.cloudvault.storage_engine.dto.BillingDtos.*;
import com.cloudvault.storage_engine.entity.User;
import com.cloudvault.storage_engine.repository.BucketRepository;
import com.cloudvault.storage_engine.repository.InvoiceRepository;
import com.cloudvault.storage_engine.repository.UserRepository;
import com.cloudvault.storage_engine.service.BillingService;
import com.cloudvault.storage_engine.service.StorageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final BucketRepository bucketRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingService billingService;

    @GetMapping("/overview")
    public ResponseEntity<AdminOverview> getOverview() {
        List<User> users = userRepository.findAll();

        long totalStorage = users.stream()
                .mapToLong(u -> bucketRepository.sumTotalSizeByUser(u))
                .sum();

        BigDecimal allTime = users.stream()
                .map(User::getTotalBilled)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate now = LocalDate.now();
        BigDecimal thisMonth = invoiceRepository.findAll().stream()
                .filter(i -> i.getBillingYear() == now.getYear()
                        && i.getBillingMonth() == now.getMonthValue())
                .map(i -> i.getAmountDue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AdminOverview overview = new AdminOverview();
        overview.setTotalUsers(users.size());
        overview.setTotalStorageBytes(totalStorage);
        overview.setTotalStorageFormatted(StorageService.formatBytes(totalStorage));
        overview.setTotalRevenueThisMonth(thisMonth);
        overview.setTotalRevenueAllTime(allTime);
        return ResponseEntity.ok(overview);
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserSummary>> getAllUsers() {
        List<UserSummary> result = userRepository.findAll().stream().map(u -> {
            UserSummary s = new UserSummary();
            s.setId(u.getId());
            s.setUsername(u.getUsername());
            s.setEmail(u.getEmail());
            s.setTenantId(u.getTenantId());
            s.setTotalBilled(u.getTotalBilled());
            long storage = bucketRepository.sumTotalSizeByUser(u);
            s.setStorageUsed(storage);
            s.setStorageFormatted(StorageService.formatBytes(storage));
            s.setActive(u.isActive());
            return s;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/billing/run/{year}/{month}")
    public ResponseEntity<String> triggerBilling(
            @PathVariable int year,
            @PathVariable int month) {
        billingService.generateForAllUsers(year, month);
        return ResponseEntity.ok("Billing complete for " + month + "/" + year);
    }

    @Data
    public static class UserSummary {
        private Long id;
        private String username;
        private String email;
        private String tenantId;
        private BigDecimal totalBilled;
        private long storageUsed;
        private String storageFormatted;
        private boolean active;
    }
}