package com.example.erp.companies;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {
    private final CompanyRepository companies;

    public CompanyController(CompanyRepository companies) { this.companies = companies; }

    @GetMapping
    public List<CompanyResponse> list() {
        return companies.findAllByOrderByNameAsc().stream().map(CompanyResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@RequestBody CompanyRequest request) {
        if (request == null || blank(request.name()) || blank(request.type()) || blank(request.currency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company name, type, and currency are required.");
        }
        String name = request.name().trim();
        if (companies.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A company with this name already exists.");
        }
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency must be a three-letter code.");
        }
        String status = blank(request.status()) ? "ACTIVE" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown company status.");
        }
        return CompanyResponse.from(companies.save(Company.create(name, request.type().trim(), currency, status,
            validColor(request.color(), "#D9ED62"))));
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private String validColor(String value, String fallback) {
        return value != null && value.matches("#[0-9A-Fa-f]{6}") ? value.toUpperCase(Locale.ROOT) : fallback;
    }

    public record CompanyRequest(String name, String type, String currency, String status, String color) { }

    public record CompanyResponse(UUID id, String name, String type, String currency, String status, String color) {
        static CompanyResponse from(Company company) {
            return new CompanyResponse(company.getId(), company.getName(), company.getType(),
                    company.getCurrency(), company.getStatus(), company.getColor());
        }
    }
}