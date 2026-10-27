package com.example.erp.accounting;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountInvoiceRepository extends JpaRepository<AccountInvoice, UUID> {
    List<AccountInvoice> findAllByOrderByDueDateAsc();
    boolean existsByInvoiceNumber(String invoiceNumber);
    boolean existsByInvoiceNumberAndIdNot(String invoiceNumber, UUID id);
    java.util.Optional<AccountInvoice> findBySalesOrderId(UUID salesOrderId);
}