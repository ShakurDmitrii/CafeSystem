package com.shakur.cafehelp.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CashPaymentCalculator {
    private CashPaymentCalculator() {
    }

    public static Settlement calculate(BigDecimal total, BigDecimal received) {
        if (total == null || total.signum() < 0) {
            throw new IllegalArgumentException("Итоговая сумма заказа некорректна");
        }

        BigDecimal normalizedTotal = total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal normalizedReceived = received == null
                ? normalizedTotal
                : received.setScale(2, RoundingMode.HALF_UP);

        if (normalizedReceived.signum() < 0) {
            throw new IllegalArgumentException("Полученная сумма не может быть отрицательной");
        }
        if (normalizedReceived.compareTo(normalizedTotal) < 0) {
            throw new IllegalArgumentException("Полученная сумма меньше итога заказа");
        }

        return new Settlement(
                normalizedReceived,
                normalizedReceived.subtract(normalizedTotal).setScale(2, RoundingMode.HALF_UP)
        );
    }

    public record Settlement(BigDecimal received, BigDecimal change) {
    }
}

