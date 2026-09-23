package com.example.erp.documents;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessDocumentRepository extends JpaRepository<BusinessDocument, UUID> {
    List<BusinessDocument> findAllByOrderByUpdatedOnDesc();
}