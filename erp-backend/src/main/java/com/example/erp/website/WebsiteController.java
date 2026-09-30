package com.example.erp.website;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/website")
public class WebsiteController {
    private final WebsitePageRepository pages;

    public WebsiteController(WebsitePageRepository pages) { this.pages = pages; }

    @GetMapping("/overview")
    public WebsiteOverview overview() {
        List<PageResponse> items = pages.findAllByOrderByUpdatedAtDesc().stream().map(PageResponse::from).toList();
        return new WebsiteOverview(items.stream().filter(item -> item.status() == WebsitePageStatus.PUBLISHED).count(),
                items.stream().filter(item -> item.status() == WebsitePageStatus.DRAFT).count(),
                items.stream().filter(PageResponse::hasContactForm).count(),
                items.stream().filter(item -> item.status() == WebsitePageStatus.PUBLISHED)
                        .mapToInt(PageResponse::monthlyVisits).sum(), items);
    }

        @PostMapping("/pages/visit")
        public void recordVisit(@RequestParam String slug) {
                pages.incrementMonthlyVisits(slug);
        }

    @PostMapping("/pages")
    public PageResponse create(@Valid @RequestBody PageRequest request) {
        return PageResponse.from(pages.save(new WebsitePage(UUID.randomUUID(), request.title(), request.slug(),
                request.contentType(), request.ownerName(), request.content())));
    }

    @PutMapping("/pages/{id}")
    public PageResponse update(@PathVariable UUID id, @Valid @RequestBody PageRequest request) {
        WebsitePage page = pages.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website page was not found."));
        page.update(request.title(), request.slug(), request.contentType(), request.ownerName(), request.content());
        return PageResponse.from(pages.save(page));
    }

    @PatchMapping("/pages/{id}/publish")
    public PageResponse publish(@PathVariable UUID id) {
        WebsitePage page = pages.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website page was not found."));
        page.publish();
        return PageResponse.from(pages.save(page));
    }

        @DeleteMapping("/pages/{id}")
        public void delete(@PathVariable UUID id) {
                WebsitePage page = pages.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website page was not found."));
                pages.delete(page);
        }

    public record WebsiteOverview(long publishedPageCount, long draftPageCount, long formPageCount,
            int monthlyVisits, List<PageResponse> pages) { }

    public record PageResponse(UUID id, String title, String slug, String contentType, String ownerName,
            int monthlyVisits, boolean hasContactForm, WebsitePageStatus status, LocalDateTime updatedAt, String content) {
        static PageResponse from(WebsitePage page) {
            return new PageResponse(page.getId(), page.getTitle(), page.getSlug(), page.getContentType(),
                    page.getOwnerName(), page.getMonthlyVisits(), page.hasContactForm(), page.getStatus(),
                    page.getUpdatedAt(), page.getContent());
        }
    }

    public record PageRequest(@NotBlank String title, @NotBlank String slug, @NotBlank String contentType,
            @NotBlank String ownerName, String content) { }
}