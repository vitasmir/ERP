package com.example.erp.pos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/pos")
public class PosController {
    private static final Set<String> PAYMENT_METHODS = Set.of("CARD", "CASH", "VOUCHER");
    private final PosTransactionRepository transactions;

    public PosController(PosTransactionRepository transactions) { this.transactions = transactions; }

    @GetMapping("/overview")
    public PosOverview overview() {
        List<PosTransactionResponse> items = transactions.findAllByOrderByOpenedAtDesc().stream()
                .map(PosTransactionResponse::from).toList();
        BigDecimal paidToday = items.stream().filter(item -> item.status() == PosTransactionStatus.PAID)
                .filter(item -> item.openedAt().toLocalDate().equals(LocalDateTime.now().toLocalDate()))
                .map(PosTransactionResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PosOverview(paidToday, items.stream().filter(item -> item.status() == PosTransactionStatus.OPEN).count(),
                items.stream().mapToInt(PosTransactionResponse::itemCount).sum(), items);
    }

        @PostMapping("/transactions")
        @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
        public PosTransactionResponse createFromEshop(@RequestBody EshopTransactionRequest request) {
                if (request == null || request.itemCount() <= 0 || request.totalAmount() == null
                                || request.totalAmount().signum() < 0 || !PAYMENT_METHODS.contains(request.paymentMethod())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-shop transaction contains invalid values.");
                }
                String receiptNumber = "ESH-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
                PosTransaction transaction = PosTransaction.fromEshop(receiptNumber, request.itemCount(),
                                request.totalAmount(), request.paymentMethod(), request.paid());
                return PosTransactionResponse.from(transactions.save(transaction));
        }

    @PatchMapping("/transactions/{id}/pay")
    public PosTransactionResponse pay(@PathVariable UUID id, @RequestBody PaymentRequest request) {
        if (!PAYMENT_METHODS.contains(request.method())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is not supported.");
        }
        PosTransaction transaction = transactions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POS transaction was not found."));
        transaction.pay(request.method());
        return PosTransactionResponse.from(transactions.save(transaction));
    }

    public record PaymentRequest(String method) { }

        public record EshopTransactionRequest(int itemCount, BigDecimal totalAmount, String paymentMethod, boolean paid) { }

    public record PosOverview(BigDecimal paidToday, long openTransactionCount, int itemCount,
            List<PosTransactionResponse> transactions) { }

    public record PosTransactionResponse(UUID id, String receiptNumber, String storeName, LocalDateTime openedAt,
            int itemCount, BigDecimal totalAmount, String paymentMethod, PosTransactionStatus status) {
        static PosTransactionResponse from(PosTransaction transaction) {
            return new PosTransactionResponse(transaction.getId(), transaction.getReceiptNumber(),
                    transaction.getStoreName(), transaction.getOpenedAt(), transaction.getItemCount(),
                    transaction.getTotalAmount(), transaction.getPaymentMethod(), transaction.getStatus());
        }
    }
}