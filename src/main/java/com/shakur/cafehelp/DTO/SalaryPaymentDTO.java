package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SalaryPaymentDTO(
        long paymentId,
        int personId,
        String entryType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String idempotencyKey,
        int authorAccountId,
        String authorName,
        Long relatedPaymentId,
        String comment,
        LocalDateTime createdAt
) {
}
