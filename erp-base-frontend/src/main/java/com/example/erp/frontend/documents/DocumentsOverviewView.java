package com.example.erp.frontend.documents;

import java.util.List;
import java.util.UUID;

public record DocumentsOverviewView(long pendingApprovalCount, long approvedCount, long categoryCount,
        List<DocumentView> documents) {
    public record DocumentView(UUID id, String title, String category, String ownerName, String referenceCode,
            String updatedOn, String status) { }
}