package com.example.erp.website;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WebsitePageRepository extends JpaRepository<WebsitePage, UUID> {
    List<WebsitePage> findAllByOrderByUpdatedAtDesc();

    @Modifying
    @Transactional
    @Query("update WebsitePage page set page.monthlyVisits = page.monthlyVisits + 1 "
            + "where page.slug = :slug and page.status = com.example.erp.website.WebsitePageStatus.PUBLISHED")
    int incrementMonthlyVisits(@Param("slug") String slug);
}