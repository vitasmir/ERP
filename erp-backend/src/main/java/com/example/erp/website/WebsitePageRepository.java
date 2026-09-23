package com.example.erp.website;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsitePageRepository extends JpaRepository<WebsitePage, UUID> {
    List<WebsitePage> findAllByOrderByUpdatedAtDesc();
}