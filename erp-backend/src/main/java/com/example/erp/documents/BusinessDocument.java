package com.example.erp.documents;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class BusinessDocument {
    @Id
    private UUID id;

    private String title;
    private String category;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "reference_code")
    private String referenceCode;

    @Column(name = "updated_on")
    private LocalDate updatedOn;

    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    protected BusinessDocument() { }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getOwnerName() { return ownerName; }
    public String getReferenceCode() { return referenceCode; }
    public LocalDate getUpdatedOn() { return updatedOn; }
    public DocumentStatus getStatus() { return status; }

    public void approve() { status = DocumentStatus.APPROVED; }
}