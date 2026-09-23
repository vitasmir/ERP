package com.example.erp.pos;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PosTransactionRepository extends JpaRepository<PosTransaction, UUID> {
    List<PosTransaction> findAllByOrderByOpenedAtDesc();
}