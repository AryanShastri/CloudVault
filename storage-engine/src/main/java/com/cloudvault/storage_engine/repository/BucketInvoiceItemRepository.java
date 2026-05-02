package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.BucketInvoiceItem;
import com.cloudvault.storage_engine.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BucketInvoiceItemRepository
        extends JpaRepository<BucketInvoiceItem, Long> {

    List<BucketInvoiceItem> findByInvoice(Invoice invoice);
}