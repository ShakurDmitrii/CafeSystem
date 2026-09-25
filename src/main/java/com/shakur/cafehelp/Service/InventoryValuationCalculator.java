package com.shakur.cafehelp.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure moving weighted-average calculations for a warehouse balance. */
public final class InventoryValuationCalculator {
    static final int SCALE = 6;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

    private InventoryValuationCalculator() {
    }

    static Balance receive(Balance current, BigDecimal receivedQuantity, BigDecimal receivedValue) {
        requireNonNegative(receivedQuantity, "receivedQuantity");
        requireNonNegative(receivedValue, "receivedValue");
        BigDecimal quantity = current.quantity().add(receivedQuantity);
        BigDecimal value = current.value().add(receivedValue);
        return balance(quantity, value);
    }

    static Issue issue(Balance current, BigDecimal requestedQuantity, boolean allowPartial) {
        requireNonNegative(requestedQuantity, "requestedQuantity");
        if (!allowPartial && requestedQuantity.compareTo(current.quantity()) > 0) {
            throw new IllegalArgumentException("Недостаточно продукта на складе");
        }

        BigDecimal issuedQuantity = allowPartial ? requestedQuantity.min(current.quantity()) : requestedQuantity;
        BigDecimal issuedValue = issuedQuantity.multiply(current.averageUnitCost()).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal remainingQuantity = current.quantity().subtract(issuedQuantity);
        BigDecimal remainingValue = remainingQuantity.signum() == 0
                ? ZERO
                : current.value().subtract(issuedValue).max(BigDecimal.ZERO);

        return new Issue(issuedQuantity, issuedValue, balance(remainingQuantity, remainingValue));
    }

    /**
     * Manual stock adjustment at the current average cost. A surplus is valued at
     * the average (or {@code fallbackUnitCost} when the balance has no cost yet),
     * a shortage leaves the balance at the average like any other issue.
     */
    static Adjustment adjust(Balance current, BigDecimal delta, BigDecimal fallbackUnitCost) {
        if (delta == null || delta.signum() == 0) {
            throw new IllegalArgumentException("Изменение количества не может быть нулевым");
        }
        if (delta.signum() < 0) {
            Issue issued = issue(current, delta.negate(), false);
            return new Adjustment(false, issued.quantity(), issued.value(), current.averageUnitCost(), issued.remaining());
        }
        BigDecimal unitCost = current.averageUnitCost().signum() > 0
                ? current.averageUnitCost()
                : (fallbackUnitCost != null ? fallbackUnitCost.max(BigDecimal.ZERO) : BigDecimal.ZERO)
                        .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal value = delta.multiply(unitCost).setScale(SCALE, RoundingMode.HALF_UP);
        return new Adjustment(true, delta, value, unitCost, receive(current, delta, value));
    }

    static Balance balance(BigDecimal quantity, BigDecimal value) {
        requireNonNegative(quantity, "quantity");
        requireNonNegative(value, "value");
        BigDecimal normalizedQuantity = quantity.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal normalizedValue = value.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal average = normalizedQuantity.signum() == 0
                ? ZERO
                : normalizedValue.divide(normalizedQuantity, SCALE, RoundingMode.HALF_UP);
        return new Balance(normalizedQuantity, normalizedValue, average);
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    public record Balance(BigDecimal quantity, BigDecimal value, BigDecimal averageUnitCost) {
        public Balance {
            requireNonNegative(quantity, "quantity");
            requireNonNegative(value, "value");
            requireNonNegative(averageUnitCost, "averageUnitCost");
        }
    }

    record Issue(BigDecimal quantity, BigDecimal value, Balance remaining) {
    }

    record Adjustment(boolean incoming, BigDecimal quantity, BigDecimal value, BigDecimal unitCost, Balance updated) {
    }
}
