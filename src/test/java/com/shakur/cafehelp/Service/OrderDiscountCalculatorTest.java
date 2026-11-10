package com.shakur.cafehelp.Service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderDiscountCalculatorTest {

    @Test
    void nullDiscountIsZero() {
        assertEquals(new BigDecimal("0.00"), OrderDiscountCalculator.normalize(null, new BigDecimal("2300")));
    }

    @Test
    void fixedDiscountIsSubtractedFromTotal() {
        BigDecimal discount = OrderDiscountCalculator.normalize(new BigDecimal("100"), new BigDecimal("2300"));
        assertEquals(new BigDecimal("2200.00"), new BigDecimal("2300.00").subtract(discount));
    }

    @Test
    void rejectsNegativeDiscount() {
        assertThrows(IllegalArgumentException.class,
                () -> OrderDiscountCalculator.normalize(new BigDecimal("-1"), new BigDecimal("100")));
    }

    @Test
    void rejectsDiscountAboveTotal() {
        assertThrows(IllegalArgumentException.class,
                () -> OrderDiscountCalculator.normalize(new BigDecimal("100.01"), new BigDecimal("100")));
    }

    @Test
    void distributesDiscountExactlyAcrossLines() {
        List<BigDecimal> shares = OrderDiscountCalculator.distribute(
                List.of(new BigDecimal("333.33"), new BigDecimal("333.33"), new BigDecimal("333.34")),
                new BigDecimal("100")
        );
        BigDecimal sum = shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), sum);
    }

    @Test
    void distributionIsCappedByLineSums() {
        // доставка не в позициях чека: скидка больше суммы строк не уводит строку в минус
        List<BigDecimal> shares = OrderDiscountCalculator.distribute(
                List.of(new BigDecimal("50"), new BigDecimal("30")),
                new BigDecimal("100")
        );
        assertEquals(new BigDecimal("50.00"), shares.get(0));
        assertEquals(new BigDecimal("30.00"), shares.get(1));
    }
}
