package com.example.erp.frontend.website;

import java.util.List;
import java.util.UUID;

public record WebsiteOverviewView(long publishedPageCount, long draftPageCount, long formPageCount,
        int monthlyVisits, List<PageView> pages) {
    public record PageView(UUID id, String title, String slug, String contentType, String ownerName,
            int monthlyVisits, boolean hasContactForm, String status, String updatedAt) { }
}