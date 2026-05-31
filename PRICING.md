# CloudVault Pricing — How It Actually Works

This document traces pricing from the moment a user performs an operation all the way to what appears on their monthly invoice. Everything here is derived directly from the source code in `BillingService.java` and `MeteringService.java`.

---

## The Six Things That Cost Money

| What | Unit | Rate | Free? |
|------|------|------|-------|
| **Storage** (data at rest) | Per GB / month | Depends on tier | No |
| **Class A Requests** (PUT, POST, LIST, COPY) | Per 1,000 requests | $0.005 | No |
| **Class B Requests** (GET, HEAD) | Per 10,000 requests | $0.0004 | No |
| **Bandwidth Out** (downloads) | Per GB (tiered) | $0.09–$0.05 | No |
| **Retrieval** (WARM/GLACIER tiers) | Per GB retrieved | $0.01–$0.03 | No |
| **Noncurrent Version Storage** | Per GB / month | 10% discount vs tier rate | No |

DELETE operations are always free. Uploads (inbound bandwidth) are always free.

---

## Step 1 — Something Happens → A UsageRecord Row Is Written

Every time a user performs an operation, `MeteringService.record()` writes a row to `usage_records`:

```java
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void record(User user, Bucket bucket, OperationType operationType,
                   long bytes, long bandwidthBytes, String objectKey) {
    LocalDate now = LocalDate.now();
    LifecycleTier tierNow = bucket != null ? bucket.getCurrentTier() : LifecycleTier.STANDARD;

    UsageRecord record = UsageRecord.builder()
            .user(user)
            .bucket(bucket)
            .operationType(operationType)
            .bytes(bytes)
            .bandwidthBytes(bandwidthBytes)
            .objectKey(objectKey)
            .tierAtTimeOfRequest(tierNow)
            .billingYear(now.getYear())
            .billingMonth(now.getMonthValue())
            .build();

    usageRecordRepository.save(record);
}
```

**Critical rule:** `record()` is only called after a fully successful operation. If an upload fails (virus detected, network error), the handler throws before reaching `record()`. No row is written. No charge occurs.

### Operation Types and Their Request Classes

| Operation Type | Request Class | Billed As |
|----------------|---------------|-----------|
| PUT (upload) | CLASS_A | $0.005/1,000 |
| POST | CLASS_A | $0.005/1,000 |
| LIST | CLASS_A | $0.005/1,000 |
| COPY | CLASS_A | $0.005/1,000 |
| GET (download) | CLASS_B | $0.0004/10,000 |
| HEAD | CLASS_B | $0.0004/10,000 |
| DELETE | FREE | No charge |

---

## Step 2 — Storage Is Measured From the Bucket, Not From Events

Storage (data at rest) is **not** calculated from `UsageRecord.bytes`. It is read directly from the `buckets` table:

```java
// In BillingService.computeBucketItemEstimate()
long storageBytes = bucket.getTotalSizeBytes();
```

`totalSizeBytes` is updated on every upload and delete. When you upload a 100MB file, `bucket.totalSizeBytes += 100MB`. When you delete it, `bucket.totalSizeBytes -= 100MB`. Storage cost reflects what you have right now, not what you uploaded this month.

---

## Step 3 — The Storage Rate Depends on Lifecycle Tier

```java
private double storageRateForTier(LifecycleTier tier) {
    return switch (tier) {
        case STANDARD        -> lifecycleRateStandard;    // $0.023/GB/month
        case WARM            -> lifecycleRateWarm;         // $0.0125/GB/month
        case INSTANT_GLACIER -> lifecycleRateInstantGlacier; // $0.004/GB/month
        case DEEP_GLACIER    -> lifecycleRateDeepGlacier; // $0.00099/GB/month
    };
}
```

A 100GB bucket costs:
- STANDARD: $2.30/month
- WARM: $1.25/month
- INSTANT_GLACIER: $0.40/month
- DEEP_GLACIER: $0.099/month

---

## Step 4 — Bandwidth Uses Tiered Pricing

Bandwidth out (downloads) uses a tiered rate — the more you transfer, the cheaper it gets:

```java
private double calcTieredBandwidthDouble(long bytes) {
    double gb = bytes / BYTES_PER_GB;
    double tier1Limit = 50 * 1024.0;   // 50 TB
    double tier2Limit = 150 * 1024.0;  // 150 TB
    double tier3Limit = 500 * 1024.0;  // 500 TB

    if (gb <= tier1Limit) {
        return gb * bwTier1;                    // $0.09/GB
    } else if (gb <= tier2Limit) {
        return tier1Limit * bwTier1 + (gb - tier1Limit) * bwTier2;   // $0.085/GB
    } else if (gb <= tier3Limit) {
        return tier1Limit * bwTier1
                + (tier2Limit - tier1Limit) * bwTier2
                + (gb - tier2Limit) * bwTier3;  // $0.07/GB
    } else {
        return tier1Limit * bwTier1
                + (tier2Limit - tier1Limit) * bwTier2
                + (tier3Limit - tier2Limit) * bwTier3
                + (gb - tier3Limit) * bwTier4;  // $0.05/GB
    }
}
```

**Bandwidth Tiers:**

| Tier | Monthly Volume | Rate |
|------|---------------|------|
| 1 | First 50 TB | $0.09/GB |
| 2 | 50–150 TB | $0.085/GB |
| 3 | 150–500 TB | $0.07/GB |
| 4 | 500+ TB | $0.05/GB |

Bandwidth is allocated proportionally across buckets:

```java
private BigDecimal allocateBandwidthCharge(long bucketBytes,
                                            long totalBytes,
                                            BigDecimal totalCharge) {
    double share = (double) bucketBytes / (double) totalBytes;
    return totalCharge.multiply(BigDecimal.valueOf(share));
}
```

---

## Step 5 — Retrieval Fees Apply to Cold Tiers

Downloading from non-STANDARD tiers incurs a retrieval charge on top of bandwidth:

```java
private double retrievalRateForTier(LifecycleTier tier) {
    return switch (tier) {
        case WARM            -> lifecycleRetrievalWarm;           // $0.01/GB
        case INSTANT_GLACIER -> lifecycleRetrievalInstantGlacier; // $0.03/GB
        case DEEP_GLACIER    -> lifecycleRetrievalDeepGlacier;    // requires restore
        default              -> 0.0;  // STANDARD — no retrieval fee
    };
}
```

---

## Step 6 — Versioning Charges

When versioning is enabled on a bucket:

```java
// Noncurrent version storage: 10% discount
double noncurrentStorageRate = storageRate * NONCURRENT_STORAGE_DISCOUNT;  // × 0.90
BigDecimal noncurrentStorageCharge = bd(noncurrentBytes / BYTES_PER_GB * noncurrentStorageRate);

// Noncurrent version bandwidth: 20% surcharge
double normalBwCharge = calcTieredBandwidthDouble(noncurrentBandwidth);
BigDecimal noncurrentBwCharge = bd(normalBwCharge * NONCURRENT_BANDWIDTH_SURCHARGE);  // × 1.20
```

**Summary:**
- **Current version storage** → Standard tier rate
- **Noncurrent version storage** → Tier rate × 0.90
- **Current version download** → Normal tiered bandwidth
- **Noncurrent version download** → Normal bandwidth × 1.20

---

## Step 7 — DEEP_GLACIER Restore Pricing

Objects in DEEP_GLACIER cannot be downloaded directly. A restore request must be submitted first:

| Speed | Restore Time (demo) | Per GB | Per Request | Access Window |
|-------|--------------------|---------|-----------  |--------------|
| EXPEDITED | 1 minute | $0.03/GB | $0.01 | 24 hours |
| STANDARD | 5 minutes | $0.02/GB | $0.0025 | 72 hours |
| BULK | 10 minutes | $0.0025/GB | Free | 168 hours |

```java
BigDecimal fee = BigDecimal.valueOf(
        (gbSize * feePerGb) + feePerRequest)
        .setScale(6, RoundingMode.HALF_UP);
```

The restore fee is charged immediately when the request is submitted, regardless of whether the user downloads within the access window.

---

## Step 8 — Invoice Generation

Invoices are generated per user per month. Each invoice contains one `BucketInvoiceItem` per active bucket:

```java
BucketInvoiceItem item = calculateBucketItem(invoice, bucket, user, year, month,
        totalUserBandwidth, totalBwCharge);

item.setStorageCharge(...)       // tier rate × storage GB
item.setClassACharge(...)        // classA requests / 1000 × $0.005
item.setClassBCharge(...)        // classB requests / 10000 × $0.0004
item.setBandwidthCharge(...)     // proportional share of tiered bandwidth
item.setRetrievalCharge(...)     // retrieval rate × bandwidth GB
item.setVersioningStorageCharge(...)  // noncurrent versions charge

item.setSubtotal(storage + classA + classB + bandwidth + retrieval + versioning)
```

The invoice `totalCharge` is the sum of all bucket subtotals.

---

## Complete Charge Reference

| # | Charge | Rate | Applied When |
|---|--------|------|--------------|
| 1 | Storage (STANDARD) | $0.023/GB/month | Always |
| 2 | Storage (WARM) | $0.0125/GB/month | Bucket in WARM tier |
| 3 | Storage (INSTANT_GLACIER) | $0.004/GB/month | Bucket in INSTANT_GLACIER |
| 4 | Storage (DEEP_GLACIER) | $0.00099/GB/month | Bucket in DEEP_GLACIER |
| 5 | Noncurrent version storage | Tier rate × 0.90 | Versioning enabled |
| 6 | Class A requests | $0.005/1,000 | PUT/POST/LIST/COPY |
| 7 | Class B requests | $0.0004/10,000 | GET/HEAD |
| 8 | Bandwidth out tier 1 | $0.09/GB | Downloads 0–50TB/mo |
| 9 | Bandwidth out tier 2 | $0.085/GB | Downloads 50–150TB/mo |
| 10 | Bandwidth out tier 3 | $0.07/GB | Downloads 150–500TB/mo |
| 11 | Bandwidth out tier 4 | $0.05/GB | Downloads 500+TB/mo |
| 12 | Noncurrent version download | Normal bandwidth × 1.20 | Old version downloaded |
| 13 | Retrieval (WARM) | $0.01/GB | Download from WARM |
| 14 | Retrieval (INSTANT_GLACIER) | $0.03/GB | Download from INSTANT_GLACIER |
| 15 | Restore (EXPEDITED) | $0.03/GB + $0.01/req | DEEP_GLACIER restore |
| 16 | Restore (STANDARD) | $0.02/GB + $0.0025/req | DEEP_GLACIER restore |
| 17 | Restore (BULK) | $0.0025/GB | DEEP_GLACIER restore |

---

## What Is Never Charged

| Scenario | Why |
|----------|-----|
| Upload (inbound bandwidth) | Uploads are free |
| Failed uploads | `record()` never called if operation throws |
| Virus-infected file rejected | File deleted before `record()` is reached |
| DELETE operations | OperationType.DELETE maps to RequestClass.FREE |
| Temp scan files | Stored under `temp-scan/` prefix, never enter DB |
| Async job polling | GET on `/upload-jobs/{id}` does not call `record()` |

---

## Billing Calculation Example

**A user with:**
- 50 GB stored in STANDARD
- 20 GB stored in WARM (versioning enabled, 5 GB noncurrent)
- 1,000 Class A requests
- 5,000 Class B requests
- 100 GB downloaded this month

**Calculation:**

```
Storage (STANDARD):   50 GB × $0.023        = $1.15
Storage (WARM):       20 GB × $0.0125       = $0.25
Versioning (WARM):    5 GB × ($0.0125×0.90) = $0.056

Class A:   1,000 / 1,000 × $0.005           = $0.005
Class B:   5,000 / 10,000 × $0.0004         = $0.0002

Bandwidth: 100 GB × $0.09                   = $9.00
Retrieval (WARM): 10 GB × $0.01             = $0.10

Total = $10.56
```
