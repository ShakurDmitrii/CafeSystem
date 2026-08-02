package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DebtPaymentDTO(
        Long paymentId,
        int orderId,
        int clientId,
        BigDecimal amount,
        BigDecimal remainingAmount,
        String paymentType,
        String idempotencyKey,
        LocalDateTime createdAt,
        boolean fullyPaid
) {
}
