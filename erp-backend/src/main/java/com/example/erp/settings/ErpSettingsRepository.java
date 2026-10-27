package com.example.erp.settings;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpSettingsRepository extends JpaRepository<ErpSettings, UUID> { }