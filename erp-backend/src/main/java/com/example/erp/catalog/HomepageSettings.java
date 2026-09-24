package com.example.erp.catalog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "homepage_settings")
public class HomepageSettings {
    @Id
    private UUID id;
    private String design;
    private String headline;
    private String subheadline;

    @Column(name = "text_x")
    private BigDecimal textX;

    @Column(name = "text_y")
    private BigDecimal textY;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected HomepageSettings() { }

    public void update(String design, String headline, String subheadline, BigDecimal textX, BigDecimal textY) {
        this.design = design;
        this.headline = headline;
        this.subheadline = subheadline;
        this.textX = textX;
        this.textY = textY;
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public String getDesign() { return design; }
    public String getHeadline() { return headline; }
    public String getSubheadline() { return subheadline; }
    public BigDecimal getTextX() { return textX; }
    public BigDecimal getTextY() { return textY; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}