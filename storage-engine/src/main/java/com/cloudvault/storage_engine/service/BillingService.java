package com.cloudvault.storage_engine.service;

import com.cloudvault.storage_engine.dto.BillingDtos.*;
import com.cloudvault.storage_engine.entity.*;
import com.cloudvault.storage_engine.enums.*;
import com.cloudvault.storage_engine.exception.ResourceNotFoundException;
import com.cloudvault.storage_engine.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final UsageRecordRepository usageRecordRepository;
    private final InvoiceRepository invoiceRepository;
    private final BucketRepository bucketRepository;
    private final UserRepository userRepository;
    private final BucketInvoiceItemRepository bucketInvoiceItemRepository;

    @Value("${billing.storage.standard}")
    private double rateStandard;

    @Value("${billing.storage.vault}")
    private double rateVault;

    @Value("${billing.storage.cold-vault}")
    private double rateColdVault;

    @Value("${billing.storage.archive}")
    private double rateArchive;

    @Value("${billing.requests.class-a}")
    private double classARate;

    @Value("${billing.requests.class-b}")
    private double classBRate;

    @Value("${billing.retrieval.vault}")
    private double retrievalVault;

    @Value("${billing.retrieval.cold-vault}")
    private double retrievalColdVault;

    @Value("${billing.retrieval.archive}")
    private double retrievalArchive;

    @Value("${billing.bandwidth.tier1}")
    private double bwTier1;

    @Value("${billing.bandwidth.tier2}")
    private double bwTier2;

    @Value("${billing.bandwidth.tier3}")
    private double bwTier3;

    @Value("${billing.bandwidth.tier4}")
    private double bwTier4;

    private static final double BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0;

    // ── USAGE SUMMARY ──────────────────────────────────────────────────

    public UsageSummary getCurrentUsageSummary(User user) {
        LocalDate now = LocalDate.now();
        return buildUsageSummary(user, now.getYear(), now.getMonthValue());
    }

    public UsageSummary getUsageSummary(User user, int year, int month) {
        return buildUsageSummary(user, year, month);
    }

    private UsageSummary buildUsageSummary(User user, int year, int month) {
        long classACount = countByClass(user, year, month, RequestClass.CLASS_A);
        long classBCount = countByClass(user, year, month, RequestClass.CLASS_B);
        long freeCount   = countByClass(user, year, month, RequestClass.FREE);
        long bandwidth   = usageRecordRepository
                .sumBandwidthByUserAndPeriod(user, year, month);
        long storageBytes = bucketRepository.sumTotalSizeByUser(user);

        BigDecimal estStorage   = bd(storageBytes / BYTES_PER_GB * rateStandard);
        BigDecimal estClassA    = bd((classACount / 1000.0) * classARate);
        BigDecimal estClassB    = bd((classBCount / 10000.0) * classBRate);
        BigDecimal estBandwidth = calcTieredBandwidth(bandwidth);
        BigDecimal estTotal     = estStorage.add(estClassA)
                .add(estClassB)
                .add(estBandwidth);

        UsageSummary s = new UsageSummary();
        s.setTenantId(user.getTenantId());
        s.setUsername(user.getUsername());
        s.setYear(year);
        s.setMonth(month);
        s.setStorageBytesUsed(storageBytes);
        s.setStorageFormatted(StorageService.formatBytes(storageBytes));
        s.setStorageGb(storageBytes / BYTES_PER_GB);
        s.setClassARequests(classACount);
        s.setClassBRequests(classBCount);
        s.setFreeRequests(freeCount);
        s.setBandwidthBytesOut(bandwidth);
        s.setBandwidthFormatted(StorageService.formatBytes(bandwidth));
        s.setBandwidthGb(bandwidth / BYTES_PER_GB);
        s.setEstimatedStorageCharge(estStorage);
        s.setEstimatedRequestCharge(estClassA.add(estClassB));
        s.setEstimatedBandwidthCharge(estBandwidth);
        s.setEstimatedTotal(estTotal);
        return s;
    }

    private long countByClass(User user, int year, int month, RequestClass rc) {
        long total = 0;
        for (OperationType op : OperationType.values()) {
            if (op.getRequestClass() == rc) {
                total += usageRecordRepository
                        .countByUserAndPeriodAndType(user, year, month, op);
            }
        }
        return total;
    }

    private long countByClassAndBucket(User user, Bucket bucket,
                                       int year, int month, RequestClass rc) {
        long total = 0;
        for (OperationType op : OperationType.values()) {
            if (op.getRequestClass() == rc) {
                total += usageRecordRepository
                        .countByUserAndBucketAndPeriodAndType(
                                user, bucket, year, month, op);
            }
        }
        return total;
    }

    // ── INVOICE GENERATION ─────────────────────────────────────────────

    @Transactional
    public Invoice generateInvoice(User user, int year, int month) {
        return invoiceRepository
                .findByUserAndBillingYearAndBillingMonth(user, year, month)
                .orElseGet(() -> createInvoice(user, year, month));
    }

    @Transactional
    public void generateForAllUsers(int year, int month) {
        log.info("Running billing for {}/{}", month, year);
        userRepository.findAll().forEach(user -> {
            try {
                generateInvoice(user, year, month);
            } catch (Exception e) {
                log.error("Invoice failed for {}: {}",
                        user.getUsername(), e.getMessage());
            }
        });
    }

    private Invoice createInvoice(User user, int year, int month) {
        List<Bucket> buckets = bucketRepository.findByUserAndActiveTrue(user);

        // Save invoice shell first
        Invoice invoice = Invoice.builder()
                .user(user)
                .billingYear(year)
                .billingMonth(month)
                .storageClass(StorageClass.STANDARD)
                .storageBytesUsed(0)
                .billableBytesUsed(0)
                .classARequests(0)
                .classBRequests(0)
                .freeRequests(0)
                .bandwidthBytesOut(0)
                .archiveRestoreBytes(0)
                .storageCapacityCharge(BigDecimal.ZERO)
                .classARequestCharge(BigDecimal.ZERO)
                .classBRequestCharge(BigDecimal.ZERO)
                .bandwidthCharge(BigDecimal.ZERO)
                .dataRetrievalCharge(BigDecimal.ZERO)
                .archiveRestoreCharge(BigDecimal.ZERO)
                .minDurationCharge(BigDecimal.ZERO)
                .totalCharge(BigDecimal.ZERO)
                .amountDue(BigDecimal.ZERO)
                .status(InvoiceStatus.GENERATED)
                .bucketItems(new ArrayList<>())
                .build();

        invoice = invoiceRepository.save(invoice);

        // Calculate per bucket item
        List<BucketInvoiceItem> items = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        long totalStorage = 0;
        long totalClassA = 0;
        long totalClassB = 0;
        long totalFree = 0;
        long totalBandwidth = 0;

        for (Bucket bucket : buckets) {
            BucketInvoiceItem item = calculateBucketItem(
                    invoice, bucket, user, year, month);
            items.add(item);
            grandTotal    = grandTotal.add(item.getSubtotal());
            totalStorage  += item.getStorageBytesUsed();
            totalClassA   += item.getClassARequests();
            totalClassB   += item.getClassBRequests();
            totalBandwidth += item.getBandwidthBytesOut();
        }

        // Save all bucket items
        bucketInvoiceItemRepository.saveAll(items);

        // Update invoice with aggregated totals
        invoice.setBucketItems(items);
        invoice.setStorageBytesUsed(totalStorage);
        invoice.setBillableBytesUsed(totalStorage);
        invoice.setClassARequests(totalClassA);
        invoice.setClassBRequests(totalClassB);
        invoice.setBandwidthBytesOut(totalBandwidth);
        invoice.setTotalCharge(grandTotal);
        invoice.setAmountDue(grandTotal);

        invoice = invoiceRepository.save(invoice);

        // Update user lifetime billing
        BigDecimal current = user.getTotalBilled() != null
                ? user.getTotalBilled() : BigDecimal.ZERO;
        user.setTotalBilled(current.add(grandTotal));
        userRepository.save(user);

        log.info("Invoice generated: user={} period={}/{} buckets={} total=${}",
                user.getUsername(), month, year, buckets.size(), grandTotal);

        return invoice;
    }

    private BucketInvoiceItem calculateBucketItem(Invoice invoice, Bucket bucket,
                                                  User user, int year, int month) {
        StorageClass sc = bucket.getStorageClass();
        long storageBytes = bucket.getTotalSizeBytes();

        long classAReqs = countByClassAndBucket(
                user, bucket, year, month, RequestClass.CLASS_A);
        long classBReqs = countByClassAndBucket(
                user, bucket, year, month, RequestClass.CLASS_B);
        long bandwidth  = usageRecordRepository
                .sumBandwidthByUserAndBucketAndPeriod(user, bucket, year, month);

        // Storage rate based on class
        double storageRate = switch (sc) {
            case STANDARD   -> rateStandard;
            case VAULT      -> rateVault;
            case COLD_VAULT -> rateColdVault;
            case ARCHIVE    -> rateArchive;
            case SMART_TIER -> rateStandard;
        };

        // Class B rate based on class
        double classBRateForClass = switch (sc) {
            case VAULT      -> 0.0040;
            case COLD_VAULT -> 0.0200;
            default         -> classBRate;
        };

        // Retrieval charge
        double retrievalRate = switch (sc) {
            case VAULT      -> retrievalVault;
            case COLD_VAULT -> retrievalColdVault;
            case ARCHIVE    -> retrievalArchive;
            default         -> 0.0;
        };

        BigDecimal storageCharge   = bd(storageBytes / BYTES_PER_GB * storageRate);
        BigDecimal classACharge    = bd((classAReqs / 1000.0) * classARate);
        BigDecimal classBCharge    = bd((classBReqs / 10000.0) * classBRateForClass);
        BigDecimal bwCharge        = calcTieredBandwidth(bandwidth);
        BigDecimal retrievalCharge = bd((bandwidth / BYTES_PER_GB) * retrievalRate);
        BigDecimal subtotal        = storageCharge
                .add(classACharge)
                .add(classBCharge)
                .add(bwCharge)
                .add(retrievalCharge);

        log.debug("Bucket [{}] class={} storage=${} classA=${} classB=${} bw=${} retrieval=${} subtotal=${}",
                bucket.getName(), sc, storageCharge, classACharge,
                classBCharge, bwCharge, retrievalCharge, subtotal);

        return BucketInvoiceItem.builder()
                .invoice(invoice)
                .bucket(bucket)
                .bucketName(bucket.getName())
                .storageClass(sc)
                .storageBytesUsed(storageBytes)
                .classARequests(classAReqs)
                .classBRequests(classBReqs)
                .bandwidthBytesOut(bandwidth)
                .storageCharge(storageCharge)
                .classACharge(classACharge)
                .classBCharge(classBCharge)
                .bandwidthCharge(bwCharge)
                .retrievalCharge(retrievalCharge)
                .subtotal(subtotal)
                .build();
    }

    // ── BANDWIDTH TIERS ────────────────────────────────────────────────

    private BigDecimal calcTieredBandwidth(long bytes) {
        if (bytes <= 0) return BigDecimal.ZERO;
        double gb = bytes / BYTES_PER_GB;
        double tier1Limit = 50 * 1024.0;
        double tier2Limit = 150 * 1024.0;
        double tier3Limit = 500 * 1024.0;
        double charge;
        if (gb <= tier1Limit) {
            charge = gb * bwTier1;
        } else if (gb <= tier2Limit) {
            charge = tier1Limit * bwTier1 + (gb - tier1Limit) * bwTier2;
        } else if (gb <= tier3Limit) {
            charge = tier1Limit * bwTier1
                    + (tier2Limit - tier1Limit) * bwTier2
                    + (gb - tier2Limit) * bwTier3;
        } else {
            charge = tier1Limit * bwTier1
                    + (tier2Limit - tier1Limit) * bwTier2
                    + (tier3Limit - tier2Limit) * bwTier3
                    + (gb - tier3Limit) * bwTier4;
        }
        return bd(charge);
    }

    // ── INVOICE LISTING ────────────────────────────────────────────────

    public List<InvoiceResponse> getInvoicesForUser(User user) {
        return invoiceRepository
                .findByUserOrderByBillingYearDescBillingMonthDesc(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public InvoiceResponse getInvoice(User user, Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Invoice not found"));
        if (!invoice.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Invoice not found");
        }
        return toResponse(invoice);
    }

    private InvoiceResponse toResponse(Invoice inv) {
        InvoiceResponse r = new InvoiceResponse();
        r.setId(inv.getId());
        r.setBillingYear(inv.getBillingYear());
        r.setBillingMonth(inv.getBillingMonth());
        r.setBillingPeriod(Month.of(inv.getBillingMonth())
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + inv.getBillingYear());
        r.setStorageBytesUsed(inv.getStorageBytesUsed());
        r.setStorageFormatted(StorageService.formatBytes(inv.getStorageBytesUsed()));
        r.setClassARequests(inv.getClassARequests());
        r.setClassBRequests(inv.getClassBRequests());
        r.setFreeRequests(inv.getFreeRequests());
        r.setBandwidthBytesOut(inv.getBandwidthBytesOut());
        r.setBandwidthFormatted(StorageService.formatBytes(inv.getBandwidthBytesOut()));
        r.setTotalCharge(inv.getTotalCharge());
        r.setAmountDue(inv.getAmountDue());
        r.setStatus(inv.getStatus());
        r.setGeneratedAt(inv.getGeneratedAt());
        r.setPaidAt(inv.getPaidAt());

        // Map bucket items
        List<BucketItemResponse> bucketItems = inv.getBucketItems()
                .stream()
                .map(item -> {
                    BucketItemResponse b = new BucketItemResponse();
                    b.setBucketName(item.getBucketName());
                    b.setStorageClass(item.getStorageClass().name());
                    b.setStorageBytesUsed(item.getStorageBytesUsed());
                    b.setStorageFormatted(
                            StorageService.formatBytes(item.getStorageBytesUsed()));
                    b.setClassARequests(item.getClassARequests());
                    b.setClassBRequests(item.getClassBRequests());
                    b.setBandwidthBytesOut(item.getBandwidthBytesOut());
                    b.setBandwidthFormatted(
                            StorageService.formatBytes(item.getBandwidthBytesOut()));
                    b.setStorageCharge(item.getStorageCharge());
                    b.setClassACharge(item.getClassACharge());
                    b.setClassBCharge(item.getClassBCharge());
                    b.setBandwidthCharge(item.getBandwidthCharge());
                    b.setRetrievalCharge(item.getRetrievalCharge());
                    b.setSubtotal(item.getSubtotal());
                    return b;
                })
                .collect(Collectors.toList());

        r.setBucketItems(bucketItems);
        return r;
    }

    private BigDecimal bd(double val) {
        return BigDecimal.valueOf(val).setScale(6, RoundingMode.HALF_UP);
    }
}