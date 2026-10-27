package com.example.erp.frontend.website;

import java.time.LocalDateTime;
import java.util.UUID;

public record PublicPageView(UUID id, String title, String slug, String contentType, String ownerName,
        int monthlyVisits, boolean hasContactForm, String status, LocalDateTime updatedAt, String content) { }