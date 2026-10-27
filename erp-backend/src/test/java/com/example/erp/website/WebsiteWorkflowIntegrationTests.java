package com.example.erp.website;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.erp.support.AbstractPostgresIntegrationTest;

class WebsiteWorkflowIntegrationTests extends AbstractPostgresIntegrationTest {
    @Autowired
    private WebsitePageRepository pages;
    private UUID pageId;

    @AfterEach
    void cleanUp() {
        if (pageId != null) pages.deleteById(pageId);
    }

    @Test
    void publishedPageIsVisibleAndVisitIsRecordedThroughHttp() throws Exception {
        String slug = "/test-public-page-" + UUID.randomUUID();
        WebsitePage page = new WebsitePage(UUID.randomUUID(), "Test public page", slug, "CONTENT",
                "Test owner", "Obsah testovací stránky");
        page.publish();
        pageId = pages.save(page).getId();

        HttpResponse<String> publicPage = send("GET", "/api/v1/website/pages/public?slug="
                + java.net.URLEncoder.encode(slug, java.nio.charset.StandardCharsets.UTF_8), null);

        assertEquals(200, publicPage.statusCode(), publicPage.body());
        assertTrue(publicPage.body().contains("Test public page"));
        assertTrue(publicPage.body().contains("Obsah testovací stránky"));

        HttpResponse<String> visit = send("POST", "/api/v1/website/pages/visit?slug="
                + java.net.URLEncoder.encode(slug, java.nio.charset.StandardCharsets.UTF_8), null);

        assertEquals(200, visit.statusCode(), visit.body());
        assertEquals(1, jdbc.queryForObject("SELECT monthly_visits FROM website_pages WHERE id = ?",
                Integer.class, pageId));
    }

    @Test
    void draftPageIsNotPubliclyVisible() throws Exception {
        String slug = "/test-draft-page-" + UUID.randomUUID();
        WebsitePage page = new WebsitePage(UUID.randomUUID(), "Test draft page", slug, "CONTENT",
                "Test owner", "Koncept");
        pageId = pages.save(page).getId();

        HttpResponse<String> response = send("GET", "/api/v1/website/pages/public?slug="
                + java.net.URLEncoder.encode(slug, java.nio.charset.StandardCharsets.UTF_8), null);

        assertEquals(404, response.statusCode(), response.body());
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}