package com.shakur.cafehelp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class BusinessTimeProvider {
    private final Clock clock;

    public BusinessTimeProvider(@Value("${app.business-zone:Europe/Moscow}") String zoneId) {
        this.clock = Clock.system(ZoneId.of(zoneId));
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public ZoneId zoneId() {
        return clock.getZone();
    }
}
