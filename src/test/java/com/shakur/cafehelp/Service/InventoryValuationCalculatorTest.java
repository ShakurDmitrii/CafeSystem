package com.shakur.cafehelp.Service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryValuationCalculatorTest {
    @Test
    void receiptUsesTheValueOfTheActualRemainingStock() {
        var afterFirstReceipt = InventoryValuationCalculator.receive(
                balance("0", "0"), decimal("100"), decimal("1000")
        );
        var afterIssue = InventoryValuationCalculator.issue(afterFirstReceipt, decimal("80"), false);
        var result = InventoryValuationCalculator.receive(
                afterIssue.remaining(), decimal("100"), decimal("1500")
        );

        assertThat(result.quantity()).isEqualByComparingTo("120.000000");
        assertThat(result.value()).isEqualByComparingTo("1700.000000");
        assertThat(result.averageUnitCost()).isEqualByComparingTo("14.166667");
    }

    @Test
    void issueKeepsTheAverageAndRemovesQuantityAndValueTogether() {
        var result = InventoryValuationCalculator.issue(balance("120", "1700"), decimal("20"), false);

        assertThat(result.quantity()).isEqualByComparingTo("20");
        assertThat(result.value()).isEqualByComparingTo("283.333340");
        assertThat(result.remaining().quantity()).isEqualByComparingTo("100.000000");
        assertThat(result.remaining().value()).isEqualByComparingTo("1416.666660");
        assertThat(result.remaining().averageUnitCost()).isEqualByComparingTo("14.166667");
    }

    @Test
    void partialIssueChargesOnlyTheQuantityActuallyAvailable() {
        var result = InventoryValuationCalculator.issue(balance("3", "30"), decimal("5"), true);

        assertThat(result.quantity()).isEqualByComparingTo("3");
        assertThat(result.value()).isEqualByComparingTo("30.000000");
        assertThat(result.remaining().quantity()).isZero();
        assertThat(result.remaining().value()).isZero();
    }

    @Test
    void exactIssueRejectsShortageWithoutChangingTheBalance() {
        assertThatThrownBy(() -> InventoryValuationCalculator.issue(balance("3", "30"), decimal("5"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Недостаточно");
    }

    private InventoryValuationCalculator.Balance balance(String quantity, String value) {
        return InventoryValuationCalculator.balance(decimal(quantity), decimal(value));
    }

    @Test
    void manualWriteOffOfWholeStockClearsItsValueBeforeNextProduction() {
        var writeOff = InventoryValuationCalculator.adjust(balance("10", "1000"), decimal("-10"), decimal("0"));

        assertThat(writeOff.incoming()).isFalse();
        assertThat(writeOff.value()).isEqualByComparingTo("1000");
        assertThat(writeOff.updated().quantity()).isEqualByComparingTo("0");
        assertThat(writeOff.updated().value()).isEqualByComparingTo("0");

        var afterProduction = InventoryValuationCalculator.receive(writeOff.updated(), decimal("10"), decimal("500"));
        assertThat(afterProduction.averageUnitCost()).isEqualByComparingTo("50");
    }

    @Test
    void manualSurplusIsValuedAtTheCurrentAverage() {
        var surplus = InventoryValuationCalculator.adjust(balance("10", "1000"), decimal("5"), decimal("1"));

        assertThat(surplus.incoming()).isTrue();
        assertThat(surplus.unitCost()).isEqualByComparingTo("100");
        assertThat(surplus.value()).isEqualByComparingTo("500");
        assertThat(surplus.updated().quantity()).isEqualByComparingTo("15");
        assertThat(surplus.updated().averageUnitCost()).isEqualByComparingTo("100");
    }

    @Test
    void manualSurplusOnEmptyStockUsesTheFallbackCost() {
        var surplus = InventoryValuationCalculator.adjust(balance("0", "0"), decimal("4"), decimal("25"));

        assertThat(surplus.value()).isEqualByComparingTo("100");
        assertThat(surplus.updated().averageUnitCost()).isEqualByComparingTo("25");
    }

    @Test
    void manualAdjustmentRejectsZeroAndShortage() {
        assertThatThrownBy(() -> InventoryValuationCalculator.adjust(balance("10", "100"), decimal("0"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InventoryValuationCalculator.adjust(balance("10", "100"), decimal("-11"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
