package com.shakur.cafehelp.Service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumableServiceRuleTest {
    @Test
    void calculatesChopsticksPerPerson() {
        assertThat(ConsumableService.calculateRuleQuantity(
                "per_person", BigDecimal.ONE, BigDecimal.ONE, 3, 0
        )).isEqualByComparingTo("3");
    }

    @Test
    void roundsMenuItemBundlesUp() {
        assertThat(ConsumableService.calculateRuleQuantity(
                "per_menu_item", new BigDecimal("100"), new BigDecimal("2"), 1, 3
        )).isEqualByComparingTo("200");
    }

    @Test
    void addsPerOrderPackagingOnce() {
        assertThat(ConsumableService.calculateRuleQuantity(
                "per_order", BigDecimal.ONE, BigDecimal.ONE, 8, 25
        )).isEqualByComparingTo("1");
    }
}
