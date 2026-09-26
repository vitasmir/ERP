package com.example.erp.companies;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "erp_companies")
public class Company {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(name = "company_type", nullable = false, length = 100)
    private String type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 7)
    private String color;

    protected Company() { }

    public static Company create(String name, String type, String currency, String status, String color) {
        Company company = new Company();
        company.id = UUID.randomUUID();
        company.name = name;
        company.type = type;
        company.currency = currency;
        company.status = status;
        company.color = color;
        return company;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public String getColor() { return color; }
}