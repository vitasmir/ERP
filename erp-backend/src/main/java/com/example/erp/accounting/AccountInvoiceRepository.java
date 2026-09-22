package com.example.erp.accounting;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountInvoiceRepository extends JpaRepository<AccountInvoice, UUID> {
    List<AccountInvoice> findAllByOrderByDueDateAsc();
}