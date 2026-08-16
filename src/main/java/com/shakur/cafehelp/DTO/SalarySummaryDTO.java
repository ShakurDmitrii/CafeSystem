package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SalarySummaryDTO(
        int personId,
        String name,
        BigDecimal dailyRate,
        int accruedShifts,
        BigDecimal accruedAmount,
        BigDecimal paidAmount,
        BigDecimal balance,
        LocalDateTime lastPaidAt
) {
}
