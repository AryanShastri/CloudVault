package com.cloudvault.storage_engine.enums;



    /**
     * Represents the current storage tier of a bucket.
     * Determines pricing, retrieval fees, and access patterns.
     *
     * Downgrade path: STANDARD → WARM → INSTANT_GLACIER → DEEP_GLACIER
     * Upgrade path:   based on request count thresholds
     */
    public enum LifecycleTier {

        /**
         * Frequently accessed data.
         * $0.023/GB/month — no retrieval fee — no minimum duration
         */
        STANDARD,

        /**
         * Infrequently accessed data.
         * $0.0125/GB/month — $0.01/GB retrieval — 30 day minimum
         */
        WARM,

        /**
         * Rarely accessed data — instant retrieval.
         * $0.004/GB/month — $0.03/GB retrieval — 90 day minimum
         */
        INSTANT_GLACIER,

        /**
         * Archive data — slow retrieval (hours).
         * $0.00099/GB/month — $0.02/GB retrieval — 180 day minimum
         */
        DEEP_GLACIER
    }

