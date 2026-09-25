package com.shakur.cafehelp.Service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashPaymentCalculatorTest {
    @Test
    void calculatesChangeUsingMoneyScale() {
        var result = CashPaymentCalculator.calculate(
                new BigDecimal("590.00"),
                new BigDecimal("1000")
        );

        assertThat(result.received()).isEqualByComparingTo("1000.00");
        assertThat(result.change()).isEqualByComparingTo("410.00");
    }

    @Test
    void acceptsExactAmountForBackwardCompatibleRequest() {
        var result = CashPaymentCalculator.calculate(new BigDecimal("590.00"), null);

        assertThat(result.received()).isEqualByComparingTo("590.00");
        assertThat(result.change()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsInsufficientAmount() {
        assertThatThrownBy(() -> CashPaymentCalculator.calculate(
                new BigDecimal("590.00"),
                new BigDecimal("589.99")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("меньше итога");
    }

    @Test
    void roundsInputBeforeComparingAndCalculating() {
        var result = CashPaymentCalculator.calculate(
                new BigDecimal("100.005"),
                new BigDecimal("150.005")
        );

        assertThat(result.received()).isEqualByComparingTo("150.01");
        assertThat(result.change()).isEqualByComparingTo("50.00");
    }
}

