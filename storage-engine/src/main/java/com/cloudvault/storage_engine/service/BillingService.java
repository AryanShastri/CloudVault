package com.cloudvault.storage_engine.service;

import com.cloudvault.storage_engine.dto.BillingDtos.*;
import com.cloudvault.storage_engine.entity.*;
import com.cloudvault.storage_engine.enums.*;
import com.cloudvault.storage_engine.exception.ResourceNotFoundException;
import com.cloudvault.storage_engine.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
    private final LifecyclePolicyRepository lifecyclePolicyRepository;
    private final ObjectVersionRepository objectVersionRepository;

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

    @Value("${lifecycle.rate.standard}")
    private double lifecycleRateStandard;

    @Value("${lifecycle.rate.warm}")
    private double lifecycleRateWarm;

    @Value("${lifecycle.rate.instant-glacier}")
    private double lifecycleRateInstantGlacier;

    @Value("${lifecycle.rate.deep-glacier}")
    private double lifecycleRateDeepGlacier;

    @Value("${lifecycle.retrieval.warm}")
    private double lifecycleRetrievalWarm;

    @Value("${lifecycle.retrieval.instant-glacier}")
    private double lifecycleRetrievalInstantGlacier;

    @Value("${lifecycle.retrieval.deep-glacier}")
    private double lifecycleRetrievalDeepGlacier;

    @Value("${lifecycle.restore.expedited.per-gb}")
    private double restoreExpeditedPerGb;

    @Value("${lifecycle.restore.expedited.per-request}")
    private double restoreExpeditedPerRequest;

    @Value("${lifecycle.restore.standard.per-gb}")
    private double restoreStandardPerGb;

    @Value("${lifecycle.restore.standard.per-request}")
    private double restoreStandardPerRequest;

    @Value("${lifecycle.restore.bulk.per-gb}")
    private double restoreBulkPerGb;

    @Value("${billing.versioning.noncurrent-discount:0.90}")
    private double noncurrentStorageDiscount;

    @Value("${billing.versioning.noncurrent-bandwidth-surcharge:1.20}")
    private double noncurrentBandwidthSurcharge;

    @Value("${billing.bandwidth.tier1-limit-gb:51200}")
    private double tier1LimitGb;

    @Value("${billing.bandwidth.tier2-limit-gb:153600}")
    private double tier2LimitGb;

    @Value("${billing.bandwidth.tier3-limit-gb:512000}")
    private double tier3LimitGb;

    private static final double BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0;


    @Cacheable(value = "currentUsage", key = "#user.id")
    public UsageSummary getCurrentUsageSummary(User user) {
        log.debug("Cache MISS — querying DB for current usage: user={}",
                user.getUsername());
        LocalDate now = LocalDate.now();
        return buildUsageSummary(user, now.getYear(), now.getMonthValue());
    }

    public UsageSummary getUsageSummary(User user, int year, int month) {
        return buildUsageSummary(user, year, month);
    }

    public PricingReference getPricingReference() {
        List<PricingLine> lines = new ArrayList<>();

        lines.add(line("Storage", "STANDARD", "per GB / month",
                lifecycleRateStandard, "Frequently accessed data"));
        lines.add(line("Storage", "WARM", "per GB / month",
                lifecycleRateWarm,
                "Infrequently accessed; 30-day minimum duration"));
        lines.add(line("Storage", "INSTANT_GLACIER", "per GB / month",
                lifecycleRateInstantGlacier,
                "Rarely accessed; instant retrieval; 90-day minimum"));
        lines.add(line("Storage", "DEEP_GLACIER", "per GB / month",
                lifecycleRateDeepGlacier,
                "Archive; slow retrieval; 180-day minimum"));


        lines.add(line("Versioning", "Current Version",
                "per GB / month",
                lifecycleRateStandard,
                "Same rate as bucket storage tier"));
        lines.add(line("Versioning", "Noncurrent Versions",
                "per GB / month",
                lifecycleRateStandard * noncurrentStorageDiscount,
                String.format("%d%% discount vs current version rate", (int) Math.round((1 - noncurrentStorageDiscount) * 100))));
        lines.add(line("Versioning", "Noncurrent Version Download",
                "per GB",
                bwTier1 * noncurrentBandwidthSurcharge,
                String.format("%d%% surcharge on standard bandwidth rate", (int) Math.round((noncurrentBandwidthSurcharge - 1) * 100))));

        lines.add(line("Requests", "Class A", "per 1,000 requests",
                classARate,
                "PUT, POST, LIST, and other write/metadata operations"));
        lines.add(line("Requests", "Class B", "per 10,000 requests",
                classBRate, "GET, HEAD, and other read operations"));

        lines.add(line("Bandwidth", "Tier 1 (0–50 TB/mo)",
                "per GB", bwTier1, null));
        lines.add(line("Bandwidth", "Tier 2 (50–150 TB/mo)",
                "per GB", bwTier2, null));
        lines.add(line("Bandwidth", "Tier 3 (150–500 TB/mo)",
                "per GB", bwTier3, null));
        lines.add(line("Bandwidth", "Tier 4 (500+ TB/mo)",
                "per GB", bwTier4, null));

        lines.add(line("Retrieval", "WARM", "per GB retrieved",
                lifecycleRetrievalWarm, null));
        lines.add(line("Retrieval", "INSTANT_GLACIER",
                "per GB retrieved",
                lifecycleRetrievalInstantGlacier, null));
        lines.add(line("Retrieval", "DEEP_GLACIER", "per GB retrieved",
                lifecycleRetrievalDeepGlacier, null));

        lines.add(line("Restore (DEEP_GLACIER)", "Expedited",
                "per GB", restoreExpeditedPerGb,
                "+ $" + restoreExpeditedPerRequest + " per request"));
        lines.add(line("Restore (DEEP_GLACIER)", "Standard",
                "per GB", restoreStandardPerGb,
                "+ $" + restoreStandardPerRequest + " per request"));
        lines.add(line("Restore (DEEP_GLACIER)", "Bulk",
                "per GB", restoreBulkPerGb,
                "Lowest cost; longest access window"));

        PricingReference ref = new PricingReference();
        ref.setLines(lines);
        return ref;
    }

    private PricingLine line(String category, String name,
                             String unit, double rate, String notes) {
        PricingLine l = new PricingLine();
        l.setCategory(category);
        l.setName(name);
        l.setUnit(unit);
        l.setRate(String.format("$%.4f", rate));
        l.setNotes(notes);
        return l;
    }

    private UsageSummary buildUsageSummary(User user,
                                           int year, int month) {
        long classACount = countByClass(
                user, year, month, RequestClass.CLASS_A);
        long classBCount = countByClass(
                user, year, month, RequestClass.CLASS_B);
        long freeCount = countByClass(
                user, year, month, RequestClass.FREE);
        long bandwidth = usageRecordRepository
                .sumBandwidthByUserAndPeriod(user, year, month);
        long storageBytes = bucketRepository.sumTotalSizeByUser(user);

        List<Bucket> buckets =
                bucketRepository.findByUserAndActiveTrue(user);
        BigDecimal totalBwCharge = calcTieredBandwidth(bandwidth);
        List<BucketItemResponse> bucketItems = buckets.stream()
                .map(b -> computeBucketItemEstimate(
                        b, user, year, month,
                        bandwidth, totalBwCharge))
                .collect(Collectors.toList());

        BigDecimal estStorage = bucketItems.stream()
                .map(BucketItemResponse::getStorageCharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estClassA = bucketItems.stream()
                .map(BucketItemResponse::getClassACharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estClassB = bucketItems.stream()
                .map(BucketItemResponse::getClassBCharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estBandwidth = bucketItems.stream()
                .map(b -> b.getBandwidthCharge()
                        .add(b.getRetrievalCharge()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estVersioning = bucketItems.stream()
                .map(BucketItemResponse::getVersioningStorageCharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estTotal = bucketItems.stream()
                .map(BucketItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
        s.setEstimatedClassACharge(estClassA);
        s.setEstimatedClassBCharge(estClassB);
        s.setEstimatedRequestCharge(estClassA.add(estClassB));
        s.setEstimatedBandwidthCharge(estBandwidth);
        s.setEstimatedVersioningCharge(estVersioning); // ← new field
        s.setEstimatedTotal(estTotal);
        s.setBucketItems(bucketItems);
        return s;
    }

    private long countByClass(User user, int year,
                              int month, RequestClass rc) {
        long total = 0;
        for (OperationType op : OperationType.values()) {
            if (op.getRequestClass() == rc) {
                total += usageRecordRepository
                        .countByUserAndPeriodAndType(
                                user, year, month, op);
            }
        }
        return total;
    }

    private long countByClassAndBucket(User user, Bucket bucket,
                                       int year, int month,
                                       RequestClass rc) {
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


    @Transactional
    @CacheEvict(value = {"invoices", "currentUsage"}, key = "#user.id")
    public Invoice generateInvoice(User user, int year, int month) {
        log.debug("Evicting cache for user={} after invoice generation",
                user.getUsername());
        return invoiceRepository
                .findByUserAndBillingYearAndBillingMonth(
                        user, year, month)
                .map(existing -> refreshInvoice(
                        existing, user, year, month))
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
        List<Bucket> buckets =
                bucketRepository.findByUserAndActiveTrue(user);

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
        return finalizeInvoice(
                invoice, user, year, month, buckets, BigDecimal.ZERO);
    }

    @Transactional
    protected Invoice refreshInvoice(Invoice invoice, User user,
                                     int year, int month) {
        BigDecimal previousDue = invoice.getAmountDue() != null
                ? invoice.getAmountDue() : BigDecimal.ZERO;

        bucketInvoiceItemRepository.deleteByInvoice(invoice);
        invoice.getBucketItems().clear();

        List<Bucket> buckets =
                bucketRepository.findByUserAndActiveTrue(user);
        invoice = finalizeInvoice(
                invoice, user, year, month, buckets, previousDue);

        log.info("Invoice refreshed: user={} period={}/{} total=${}",
                user.getUsername(), month, year,
                invoice.getAmountDue());
        return invoice;
    }

    private Invoice finalizeInvoice(Invoice invoice, User user,
                                    int year, int month,
                                    List<Bucket> buckets,
                                    BigDecimal previousDue) {
        long totalUserBandwidth = usageRecordRepository
                .sumBandwidthByUserAndPeriod(user, year, month);
        BigDecimal totalBwCharge =
                calcTieredBandwidth(totalUserBandwidth);

        List<BucketInvoiceItem> items = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        long totalStorage = 0;
        long totalClassA = 0;
        long totalClassB = 0;
        long totalBandwidth = 0;
        BigDecimal totalStorageCharge = BigDecimal.ZERO;
        BigDecimal totalClassACharge = BigDecimal.ZERO;
        BigDecimal totalClassBCharge = BigDecimal.ZERO;
        BigDecimal totalBwChargeSum = BigDecimal.ZERO;
        BigDecimal totalRetrievalCharge = BigDecimal.ZERO;
        BigDecimal totalVersioningCharge = BigDecimal.ZERO; // ← new

        for (Bucket bucket : buckets) {
            BucketInvoiceItem item = calculateBucketItem(
                    invoice, bucket, user, year, month,
                    totalUserBandwidth, totalBwCharge);
            items.add(item);
            grandTotal = grandTotal.add(item.getSubtotal());
            totalStorage += item.getStorageBytesUsed();
            totalClassA += item.getClassARequests();
            totalClassB += item.getClassBRequests();
            totalBandwidth += item.getBandwidthBytesOut();
            totalStorageCharge = totalStorageCharge
                    .add(item.getStorageCharge());
            totalClassACharge = totalClassACharge
                    .add(item.getClassACharge());
            totalClassBCharge = totalClassBCharge
                    .add(item.getClassBCharge());
            totalBwChargeSum = totalBwChargeSum
                    .add(item.getBandwidthCharge());
            totalRetrievalCharge = totalRetrievalCharge
                    .add(item.getRetrievalCharge());

            if (item.getVersioningStorageCharge() != null) {
                totalVersioningCharge = totalVersioningCharge
                        .add(item.getVersioningStorageCharge());
            }

        }

        bucketInvoiceItemRepository.saveAll(items);

        invoice.setBucketItems(items);
        invoice.setStorageBytesUsed(totalStorage);
        invoice.setBillableBytesUsed(totalStorage);
        invoice.setClassARequests(totalClassA);
        invoice.setClassBRequests(totalClassB);
        invoice.setBandwidthBytesOut(totalBandwidth);
        invoice.setStorageCapacityCharge(totalStorageCharge);
        invoice.setClassARequestCharge(totalClassACharge);
        invoice.setClassBRequestCharge(totalClassBCharge);
        invoice.setBandwidthCharge(totalBwChargeSum);
        invoice.setDataRetrievalCharge(totalRetrievalCharge);
        invoice.setTotalCharge(grandTotal);
        invoice.setAmountDue(grandTotal);

        invoice = invoiceRepository.save(invoice);

        BigDecimal lifetime = user.getTotalBilled() != null
                ? user.getTotalBilled() : BigDecimal.ZERO;
        user.setTotalBilled(
                lifetime.subtract(previousDue).add(grandTotal));
        userRepository.save(user);

        return invoice;
    }

    private double storageRateForTier(LifecycleTier tier) {
        if (tier == null) return lifecycleRateStandard;
        return switch (tier) {
            case STANDARD -> lifecycleRateStandard;
            case WARM -> lifecycleRateWarm;
            case INSTANT_GLACIER -> lifecycleRateInstantGlacier;
            case DEEP_GLACIER -> lifecycleRateDeepGlacier;
        };
    }

    private double retrievalRateForTier(LifecycleTier tier) {
        if (tier == null) return 0.0;
        return switch (tier) {
            case WARM -> lifecycleRetrievalWarm;
            case INSTANT_GLACIER -> lifecycleRetrievalInstantGlacier;
            case DEEP_GLACIER -> lifecycleRetrievalDeepGlacier;
            default -> 0.0;
        };
    }


    private BigDecimal calculateVersioningCharge(Bucket bucket,
                                                 User user,
                                                 int year,
                                                 int month) {
        try {

            LifecyclePolicy policy = lifecyclePolicyRepository
                    .findByBucketAndActiveTrue(bucket)
                    .orElse(null);

            if (policy == null || !policy.isVersioningEnabled()) {
                return BigDecimal.ZERO;
            }

            LifecycleTier tier = bucket.getCurrentTier() != null
                    ? bucket.getCurrentTier() : LifecycleTier.STANDARD;
            double storageRate = storageRateForTier(tier);


            long noncurrentBytes = objectVersionRepository
                    .sumNoncurrentVersionSizeByBucket(bucket);

            double noncurrentStorageRate =
                    storageRate * noncurrentStorageDiscount;
            BigDecimal noncurrentStorageCharge = bd(
                    noncurrentBytes / BYTES_PER_GB
                            * noncurrentStorageRate);


            long noncurrentBandwidth = usageRecordRepository
                    .sumNoncurrentVersionBandwidthByBucketAndPeriod(
                            bucket, year, month);


            double normalBwCharge = calcTieredBandwidthDouble(
                    noncurrentBandwidth);
            BigDecimal noncurrentBwCharge = bd(
                    normalBwCharge * noncurrentBandwidthSurcharge);

            BigDecimal total = noncurrentStorageCharge
                    .add(noncurrentBwCharge);

            if (total.compareTo(BigDecimal.ZERO) > 0) {
                log.debug("Bucket [{}] versioning: " +
                                "noncurrentBytes={} storageCharge={} " +
                                "noncurrentBandwidth={} bwCharge={}",
                        bucket.getName(),
                        noncurrentBytes, noncurrentStorageCharge,
                        noncurrentBandwidth, noncurrentBwCharge);
            }

            return total;

        } catch (Exception e) {
            log.error("Error calculating versioning charge " +
                            "for bucket {}: {}",
                    bucket.getName(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }



    private BucketItemResponse computeBucketItemEstimate(
            Bucket bucket, User user, int year, int month,
            long totalUserBandwidth, BigDecimal totalBwCharge) {

        LifecycleTier tier = bucket.getCurrentTier() != null
                ? bucket.getCurrentTier() : LifecycleTier.STANDARD;
        long storageBytes = bucket.getTotalSizeBytes();

        long classAReqs = countByClassAndBucket(
                user, bucket, year, month, RequestClass.CLASS_A);
        long classBReqs = countByClassAndBucket(
                user, bucket, year, month, RequestClass.CLASS_B);
        long bandwidth = usageRecordRepository
                .sumBandwidthByUserAndBucketAndPeriod(
                        user, bucket, year, month);

        double storageRate = storageRateForTier(tier);
        double retrievalRate = retrievalRateForTier(tier);

        BigDecimal storageCharge = bd(
                storageBytes / BYTES_PER_GB * storageRate);
        BigDecimal classACharge = bd(
                (classAReqs / 1000.0) * classARate);
        BigDecimal classBCharge = bd(
                (classBReqs / 10000.0) * classBRate);
        BigDecimal bwCharge = allocateBandwidthCharge(
                bandwidth, totalUserBandwidth, totalBwCharge);
        BigDecimal retrievalCharge = bd(
                (bandwidth / BYTES_PER_GB) * retrievalRate);


        BigDecimal versioningCharge = calculateVersioningCharge(
                bucket, user, year, month);


        BigDecimal subtotal = storageCharge
                .add(classACharge)
                .add(classBCharge)
                .add(bwCharge)
                .add(retrievalCharge)
                .add(versioningCharge);

        BucketItemResponse b = new BucketItemResponse();
        b.setBucketName(bucket.getName());
        b.setStorageClass(tier.name());
        b.setStorageBytesUsed(storageBytes);
        b.setStorageFormatted(StorageService.formatBytes(storageBytes));
        b.setClassARequests(classAReqs);
        b.setClassBRequests(classBReqs);
        b.setBandwidthBytesOut(bandwidth);
        b.setBandwidthFormatted(StorageService.formatBytes(bandwidth));
        b.setStorageCharge(storageCharge);
        b.setClassACharge(classACharge);
        b.setClassBCharge(classBCharge);
        b.setBandwidthCharge(bwCharge);
        b.setRetrievalCharge(retrievalCharge);
        b.setVersioningStorageCharge(versioningCharge);
        b.setSubtotal(subtotal);
        return b;
    }

    private BigDecimal allocateBandwidthCharge(long bucketBytes,
                                               long totalBytes,
                                               BigDecimal totalCharge) {
        if (totalBytes <= 0
                || totalCharge.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        double share = (double) bucketBytes / (double) totalBytes;
        return totalCharge
                .multiply(BigDecimal.valueOf(share))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private BucketInvoiceItem calculateBucketItem(
            Invoice invoice, Bucket bucket,
            User user, int year, int month,
            long totalUserBandwidth, BigDecimal totalBwCharge) {

        BucketItemResponse est = computeBucketItemEstimate(
                bucket, user, year, month,
                totalUserBandwidth, totalBwCharge);

        LifecycleTier tier = bucket.getCurrentTier() != null
                ? bucket.getCurrentTier() : LifecycleTier.STANDARD;

        log.debug("Bucket [{}] tier={} storage=${} classA=${} " +
                        "classB=${} bw=${} retrieval=${} " +
                        "versioning=${} subtotal=${}",
                bucket.getName(), tier,
                est.getStorageCharge(), est.getClassACharge(),
                est.getClassBCharge(), est.getBandwidthCharge(),
                est.getRetrievalCharge(),
                est.getVersioningStorageCharge(),
                est.getSubtotal());

        return BucketInvoiceItem.builder()
                .invoice(invoice)
                .bucket(bucket)
                .bucketName(est.getBucketName())
                .storageClass(bucket.getStorageClass())
                .lifecycleTier(tier.name())
                .storageBytesUsed(est.getStorageBytesUsed())
                .classARequests(est.getClassARequests())
                .classBRequests(est.getClassBRequests())
                .bandwidthBytesOut(est.getBandwidthBytesOut())
                .storageCharge(est.getStorageCharge())
                .classACharge(est.getClassACharge())
                .classBCharge(est.getClassBCharge())
                .bandwidthCharge(est.getBandwidthCharge())
                .retrievalCharge(est.getRetrievalCharge())

                .versioningStorageCharge(
                        est.getVersioningStorageCharge())

                .subtotal(est.getSubtotal())
                .build();
    }

    // ── BANDWIDTH TIERS ────────────────────────────────────────────────

    private BigDecimal calcTieredBandwidth(long bytes) {
        return bd(calcTieredBandwidthDouble(bytes));
    }


    private double calcTieredBandwidthDouble(long bytes) {
        if (bytes <= 0) return 0.0;
        double gb = bytes / BYTES_PER_GB;
        double tier1Limit = tier1LimitGb;
        double tier2Limit = tier2LimitGb;
        double tier3Limit = tier3LimitGb;

        if (gb <= tier1Limit) {
            return gb * bwTier1;
        } else if (gb <= tier2Limit) {
            return tier1Limit * bwTier1
                    + (gb - tier1Limit) * bwTier2;
        } else if (gb <= tier3Limit) {
            return tier1Limit * bwTier1
                    + (tier2Limit - tier1Limit) * bwTier2
                    + (gb - tier2Limit) * bwTier3;
        } else {
            return tier1Limit * bwTier1
                    + (tier2Limit - tier1Limit) * bwTier2
                    + (tier3Limit - tier2Limit) * bwTier3
                    + (gb - tier3Limit) * bwTier4;
        }
    }



    @Cacheable(value = "invoices", key = "#user.id")
    public List<InvoiceResponse> getInvoicesForUser(User user) {
        log.debug("Cache MISS — querying DB for invoices: user={}",
                user.getUsername());
        return invoiceRepository
                .findByUserOrderByBillingYearDescBillingMonthDesc(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public InvoiceResponse getInvoice(User user, Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invoice not found"));
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
        r.setBillingPeriod(
                Month.of(inv.getBillingMonth())
                        .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                        + " " + inv.getBillingYear());
        r.setStorageBytesUsed(inv.getStorageBytesUsed());
        r.setStorageFormatted(
                StorageService.formatBytes(inv.getStorageBytesUsed()));
        r.setClassARequests(inv.getClassARequests());
        r.setClassBRequests(inv.getClassBRequests());
        r.setFreeRequests(inv.getFreeRequests());
        r.setBandwidthBytesOut(inv.getBandwidthBytesOut());
        r.setBandwidthFormatted(
                StorageService.formatBytes(inv.getBandwidthBytesOut()));
        r.setTotalCharge(inv.getTotalCharge());
        r.setAmountDue(inv.getAmountDue());
        r.setStatus(inv.getStatus());
        r.setGeneratedAt(inv.getGeneratedAt());
        r.setPaidAt(inv.getPaidAt());

        List<BucketItemResponse> bucketItems = inv.getBucketItems()
                .stream()
                .map(item -> {
                    BucketItemResponse b = new BucketItemResponse();
                    b.setBucketName(item.getBucketName());
                    b.setStorageClass(
                            item.getLifecycleTier() != null
                                    ? item.getLifecycleTier()
                                    : item.getStorageClass().name());
                    b.setStorageBytesUsed(item.getStorageBytesUsed());
                    b.setStorageFormatted(StorageService.formatBytes(
                            item.getStorageBytesUsed()));
                    b.setClassARequests(item.getClassARequests());
                    b.setClassBRequests(item.getClassBRequests());
                    b.setBandwidthBytesOut(item.getBandwidthBytesOut());
                    b.setBandwidthFormatted(StorageService.formatBytes(
                            item.getBandwidthBytesOut()));
                    b.setStorageCharge(item.getStorageCharge());
                    b.setClassACharge(item.getClassACharge());
                    b.setClassBCharge(item.getClassBCharge());
                    b.setBandwidthCharge(item.getBandwidthCharge());
                    b.setRetrievalCharge(item.getRetrievalCharge());

                    b.setVersioningStorageCharge(
                            item.getVersioningStorageCharge() != null
                                    ? item.getVersioningStorageCharge()
                                    : BigDecimal.ZERO);
                    b.setSubtotal(item.getSubtotal());
                    return b;
                })
                .collect(Collectors.toList());

        r.setBucketItems(bucketItems);
        return r;
    }

    private BigDecimal bd(double val) {
        return BigDecimal.valueOf(val)
                .setScale(6, RoundingMode.HALF_UP);
    }
}