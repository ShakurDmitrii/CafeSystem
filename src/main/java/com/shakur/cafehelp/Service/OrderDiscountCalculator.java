package com.shakur.cafehelp.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Фиксированная скидка в рублях, вычитаемая из итога заказа.
 */
public final class OrderDiscountCalculator {
    private OrderDiscountCalculator() {
    }

    /**
     * Проверяет скидку относительно итога до скидки и возвращает её с точностью до копеек.
     */
    public static BigDecimal normalize(BigDecimal discount, BigDecimal grossTotal) {
        if (discount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal normalized = discount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException("Скидка не может быть отрицательной");
        }
        BigDecimal gross = grossTotal == null ? BigDecimal.ZERO : grossTotal.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(gross) > 0) {
            throw new IllegalArgumentException("Скидка больше суммы заказа");
        }
        return normalized;
    }

    /**
     * Распределяет скидку по строкам пропорционально их суммам. Сумма долей равна скидке
     * (если скидка не больше суммы строк), остаток копеек уходит в самую дорогую строку.
     */
    public static List<BigDecimal> distribute(List<BigDecimal> lineSums, BigDecimal discount) {
        List<BigDecimal> shares = new ArrayList<>(lineSums.size());
        BigDecimal total = BigDecimal.ZERO;
        int largest = -1;
        for (int i = 0; i < lineSums.size(); i++) {
            BigDecimal sum = lineSums.get(i) == null ? BigDecimal.ZERO : lineSums.get(i);
            total = total.add(sum);
            if (largest < 0 || sum.compareTo(lineSums.get(largest)) > 0) {
                largest = i;
            }
            shares.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        if (discount == null || discount.signum() <= 0 || total.signum() <= 0) {
            return shares;
        }
        BigDecimal target = discount.min(total).setScale(2, RoundingMode.HALF_UP);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < lineSums.size(); i++) {
            BigDecimal sum = lineSums.get(i) == null ? BigDecimal.ZERO : lineSums.get(i);
            BigDecimal share = target.multiply(sum).divide(total, 2, RoundingMode.DOWN);
            shares.set(i, share);
            allocated = allocated.add(share);
        }
        BigDecimal remainder = target.subtract(allocated);
        if (remainder.signum() != 0) {
            for (int i = 0; i < lineSums.size() && remainder.signum() > 0; i++) {
                int idx = (largest + i) % lineSums.size();
                BigDecimal sum = lineSums.get(idx) == null ? BigDecimal.ZERO : lineSums.get(idx);
                BigDecimal room = sum.subtract(shares.get(idx));
                BigDecimal add = room.min(remainder);
                if (add.signum() > 0) {
                    shares.set(idx, shares.get(idx).add(add));
                    remainder = remainder.subtract(add);
                }
            }
        }
        return shares;
    }
}
