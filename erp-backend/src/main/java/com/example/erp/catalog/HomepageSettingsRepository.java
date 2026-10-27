package com.example.erp.catalog;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HomepageSettingsRepository extends JpaRepository<HomepageSettings, UUID> { }