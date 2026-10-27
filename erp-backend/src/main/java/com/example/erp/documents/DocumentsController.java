package com.example.erp.documents;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentsController {
    private final BusinessDocumentRepository documents;

    public DocumentsController(BusinessDocumentRepository documents) { this.documents = documents; }

    @GetMapping("/overview")
    public DocumentsOverview overview() {
        List<DocumentResponse> items = documents.findAllByOrderByUpdatedOnDesc().stream().map(DocumentResponse::from).toList();
        return new DocumentsOverview(items.stream().filter(item -> item.status() == DocumentStatus.PENDING_APPROVAL).count(),
                items.stream().filter(item -> item.status() == DocumentStatus.APPROVED).count(),
                items.stream().map(DocumentResponse::category).distinct().count(), items);
    }

    @PatchMapping("/{id}/approve")
    public DocumentResponse approve(@PathVariable UUID id) {
        BusinessDocument document = documents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document was not found."));
        document.approve();
        return DocumentResponse.from(documents.save(document));
    }

    public record DocumentsOverview(long pendingApprovalCount, long approvedCount, long categoryCount,
            List<DocumentResponse> documents) { }

    public record DocumentResponse(UUID id, String title, String category, String ownerName, String referenceCode,
            LocalDate updatedOn, DocumentStatus status) {
        static DocumentResponse from(BusinessDocument document) {
            return new DocumentResponse(document.getId(), document.getTitle(), document.getCategory(),
                    document.getOwnerName(), document.getReferenceCode(), document.getUpdatedOn(), document.getStatus());
        }
    }
}