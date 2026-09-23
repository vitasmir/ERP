package com.example.erp.website;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "website_pages")
public class WebsitePage {
    @Id
    private UUID id;

    private String title;
    private String slug;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "monthly_visits")
    private int monthlyVisits;

    @Column(name = "has_contact_form")
    private boolean hasContactForm;

    @Enumerated(EnumType.STRING)
    private WebsitePageStatus status;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected WebsitePage() { }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getContentType() { return contentType; }
    public String getOwnerName() { return ownerName; }
    public int getMonthlyVisits() { return monthlyVisits; }
    public boolean hasContactForm() { return hasContactForm; }
    public WebsitePageStatus getStatus() { return status; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void publish() {
        status = WebsitePageStatus.PUBLISHED;
        updatedAt = LocalDateTime.now();
    }
}