package com.cloudvault.storage_engine.repository;

import com.cloudvault.storage_engine.entity.Invoice;
import com.cloudvault.storage_engine.entity.User;
import com.cloudvault.storage_engine.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByUserOrderByBillingYearDescBillingMonthDesc(User user);

    Optional<Invoice> findByUserAndBillingYearAndBillingMonth(
            User user, int year, int month);

    List<Invoice> findByStatus(InvoiceStatus status);
}