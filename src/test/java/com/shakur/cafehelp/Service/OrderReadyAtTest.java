package com.shakur.cafehelp.Service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OrderReadyAtTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 10, 19, 0, 20);

    @Test
    void minutesAreRoundedUpFromCreationToReadyAt() {
        assertThat(OrderService.minutesBetween(CREATED, LocalDateTime.of(2026, 11, 10, 19, 30))).isEqualTo(30.0);
        assertThat(OrderService.minutesBetween(CREATED, LocalDateTime.of(2026, 11, 10, 19, 30, 21))).isEqualTo(31.0);
    }

    @Test
    void readyAtInTheNearFutureGivesAtLeastOneMinute() {
        assertThat(OrderService.minutesBetween(CREATED, CREATED.plusSeconds(5))).isEqualTo(1.0);
    }

    @Test
    void readyAtNextDayIsCountedInMinutes() {
        assertThat(OrderService.minutesBetween(CREATED, LocalDateTime.of(2026, 11, 11, 12, 0, 20))).isEqualTo(17 * 60.0);
    }
}
