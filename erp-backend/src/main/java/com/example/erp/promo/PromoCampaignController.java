package com.example.erp.promo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.catalog.Product;
import com.example.erp.catalog.ProductRepository;
import com.example.erp.purchase.Supplier;
import com.example.erp.purchase.SupplierRepository;

@RestController
@RequestMapping("/api/v1/promo-campaigns")
public class PromoCampaignController {
    private final PromoCampaignRepository campaigns;
    private final ProductRepository products;
    private final SupplierRepository suppliers;

    public PromoCampaignController(PromoCampaignRepository campaigns, ProductRepository products,
            SupplierRepository suppliers) {
        this.campaigns = campaigns;
        this.products = products;
        this.suppliers = suppliers;
    }

    @GetMapping
    public List<PromoCampaignResponse> list() {
        return campaigns.findAllByOrderByStartsOnDesc().stream()
            .map(campaign -> PromoCampaignResponse.from(campaign, products.findById(campaign.getProductId())
                .map(Product::getImageUrl).orElse(null)))
            .toList();
    }

        @GetMapping("/options")
        public CampaignOptions options() {
        return new CampaignOptions(products.findAllByOrderByNameAsc().stream()
            .map(product -> new ProductOption(product.getId(), product.getName(), product.getUnit(), product.getImageUrl())).toList(),
            suppliers.findAllByOrderByNameAsc().stream()
                .map(supplier -> new SupplierOption(supplier.getId(), supplier.getName())).toList());
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public PromoCampaignResponse create(@RequestBody CreateCampaignRequest request) {
            validate(request);
        Product product = products.findById(request.productId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product was not found."));
        Supplier supplier = suppliers.findById(request.supplierId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier was not found."));
        PromoCampaign campaign = new PromoCampaign(UUID.randomUUID(), request.name().trim(), product.getId(),
            supplier.getId(), request.startsOn(), request.endsOn(), request.regularPrice(), request.promoPrice(),
            request.supplierPurchasePrice(), request.plannedQuantity(), request.marketingContribution());
        return PromoCampaignResponse.from(campaigns.save(campaign), product.getImageUrl());
        }

    private void validate(CreateCampaignRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()
                || request.productId() == null || request.supplierId() == null || request.startsOn() == null
                || request.endsOn() == null || request.endsOn().isBefore(request.startsOn())
                || request.regularPrice() == null || request.promoPrice() == null
                || request.supplierPurchasePrice() == null || request.regularPrice().signum() < 0
                || request.promoPrice().signum() < 0 || request.supplierPurchasePrice().signum() < 0
                || request.plannedQuantity() < 0 || request.marketingContribution() == null
                || request.marketingContribution().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign contains invalid values.");
        }
    }

    @PatchMapping("/{id}/status")
    public PromoCampaignResponse updateStatus(@PathVariable UUID id, @RequestBody CampaignStatusUpdate update) {
        PromoCampaign campaign = campaigns.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promo campaign was not found."));
        campaign.changeStatus(update.status());
        String imageUrl = products.findById(campaign.getProductId()).map(Product::getImageUrl).orElse(null);
        return PromoCampaignResponse.from(campaigns.save(campaign), imageUrl);
    }

        @PutMapping("/{id}")
        public PromoCampaignResponse update(@PathVariable UUID id, @RequestBody CreateCampaignRequest request) {
        validate(request);
        PromoCampaign campaign = campaigns.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promo campaign was not found."));
        Product product = products.findById(request.productId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product was not found."));
        Supplier supplier = suppliers.findById(request.supplierId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier was not found."));
        campaign.updateDetails(request.name().trim(), product.getId(), supplier.getId(), request.startsOn(), request.endsOn(),
            request.regularPrice(), request.promoPrice(), request.supplierPurchasePrice(), request.plannedQuantity(),
            request.marketingContribution());
        return PromoCampaignResponse.from(campaigns.save(campaign), product.getImageUrl());
        }

    public record CampaignStatusUpdate(CampaignStatus status) { }

        public record CreateCampaignRequest(String name, UUID productId, UUID supplierId, LocalDate startsOn,
            LocalDate endsOn, BigDecimal regularPrice, BigDecimal promoPrice, BigDecimal supplierPurchasePrice,
            int plannedQuantity, BigDecimal marketingContribution) { }

        public record CampaignOptions(List<ProductOption> products, List<SupplierOption> suppliers) { }
        public record ProductOption(UUID id, String name, String unit, String imageUrl) { }
        public record SupplierOption(UUID id, String name) { }

        public record PromoCampaignResponse(UUID id, String name, UUID productId, UUID supplierId, CampaignStatus status,
            LocalDate startsOn, LocalDate endsOn, BigDecimal regularPrice, BigDecimal promoPrice,
            BigDecimal supplierPurchasePrice, int plannedQuantity, int actualQuantity,
            BigDecimal marketingContribution, String imageUrl) {
        static PromoCampaignResponse from(PromoCampaign campaign, String imageUrl) {
            return new PromoCampaignResponse(campaign.getId(), campaign.getName(), campaign.getProductId(),
                campaign.getSupplierId(), campaign.getStatus(), campaign.getStartsOn(), campaign.getEndsOn(),
                campaign.getRegularPrice(), campaign.getPromoPrice(), campaign.getSupplierPurchasePrice(),
                campaign.getPlannedQuantity(), campaign.getActualQuantity(), campaign.getMarketingContribution(), imageUrl);
        }
    }
}