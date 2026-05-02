package com.cloudvault.storage_engine.controller;

import com.cloudvault.storage_engine.dto.BillingDtos.*;
import com.cloudvault.storage_engine.entity.User;
import com.cloudvault.storage_engine.repository.UserRepository;
import com.cloudvault.storage_engine.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.cloudvault.storage_engine.entity.Invoice;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final UserRepository userRepository;

    @GetMapping("/usage/current")
    public ResponseEntity<UsageSummary> getCurrentUsage(Authentication auth) {
        return ResponseEntity.ok(
                billingService.getCurrentUsageSummary(getUser(auth)));
    }

    @GetMapping("/usage/{year}/{month}")
    public ResponseEntity<UsageSummary> getUsageForPeriod(
            Authentication auth,
            @PathVariable int year,
            @PathVariable int month) {
        return ResponseEntity.ok(
                billingService.getUsageSummary(getUser(auth), year, month));
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceResponse>> getInvoices(Authentication auth) {
        return ResponseEntity.ok(
                billingService.getInvoicesForUser(getUser(auth)));
    }

    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<InvoiceResponse> getInvoice(
            Authentication auth,
            @PathVariable Long invoiceId) {
        return ResponseEntity.ok(
                billingService.getInvoice(getUser(auth), invoiceId));
    }

    @PostMapping("/invoices/generate")
    public ResponseEntity<InvoiceResponse> generateInvoice(
            Authentication auth,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        User user = getUser(auth);
        Invoice inv = billingService.generateInvoice(user, y, m);
        return ResponseEntity.ok(billingService.getInvoice(user, inv.getId()));
    }

    private User getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}