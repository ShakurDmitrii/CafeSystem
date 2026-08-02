package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.config.BusinessTimeProvider;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VkLinkAttemptService {
    private static final int MAX_FAILURES = 5;
    private static final Table<?> ATTEMPT = DSL.table(DSL.name("sales", "client_vk_link_attempt"));
    private static final Field<Long> USER_ID = DSL.field(DSL.name("vk_user_id"), Long.class);
    private static final Field<Integer> FAILED = DSL.field(DSL.name("failed_attempts"), Integer.class);
    private static final Field<LocalDateTime> WINDOW_STARTED = DSL.field(DSL.name("window_started_at"), LocalDateTime.class);
    private static final Field<LocalDateTime> BLOCKED_UNTIL = DSL.field(DSL.name("blocked_until"), LocalDateTime.class);
    private static final Field<LocalDateTime> UPDATED_AT = DSL.field(DSL.name("updated_at"), LocalDateTime.class);

    private final DSLContext dsl;
    private final BusinessTimeProvider businessTime;

    public VkLinkAttemptService(DSLContext dsl, BusinessTimeProvider businessTime) {
        this.dsl = dsl;
        this.businessTime = businessTime;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureAllowed(long vkUserId) {
        LocalDateTime now = businessTime.now();
        Record row = dsl.select(FAILED, WINDOW_STARTED, BLOCKED_UNTIL)
                .from(ATTEMPT)
                .where(USER_ID.eq(vkUserId))
                .forUpdate()
                .fetchOne();
        if (row == null) {
            return;
        }
        LocalDateTime blockedUntil = row.get(BLOCKED_UNTIL);
        if (blockedUntil != null && blockedUntil.isAfter(now)) {
            throw new IllegalStateException("Слишком много неверных попыток; попробуйте позже");
        }
        LocalDateTime window = row.get(WINDOW_STARTED);
        if (window == null || !window.isAfter(now.minusMinutes(15))) {
            dsl.deleteFrom(ATTEMPT).where(USER_ID.eq(vkUserId)).execute();
            return;
        }
        if (row.get(FAILED) != null && row.get(FAILED) >= MAX_FAILURES) {
            throw new IllegalStateException("Слишком много неверных попыток; попробуйте позже");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(long vkUserId) {
        LocalDateTime now = businessTime.now();
        Record row = dsl.select(FAILED, WINDOW_STARTED)
                .from(ATTEMPT)
                .where(USER_ID.eq(vkUserId))
                .forUpdate()
                .fetchOne();
        if (row == null) {
            dsl.insertInto(ATTEMPT)
                    .columns(USER_ID, FAILED, WINDOW_STARTED, UPDATED_AT)
                    .values(vkUserId, 1, now, now)
                    .execute();
            return;
        }
        LocalDateTime window = row.get(WINDOW_STARTED);
        int failures = window == null || !window.isAfter(now.minusMinutes(15))
                ? 1
                : (row.get(FAILED) != null ? row.get(FAILED) : 0) + 1;
        dsl.update(ATTEMPT)
                .set(FAILED, failures)
                .set(WINDOW_STARTED, failures == 1 ? now : window)
                .set(BLOCKED_UNTIL, failures >= MAX_FAILURES ? now.plusMinutes(15) : null)
                .set(UPDATED_AT, now)
                .where(USER_ID.eq(vkUserId))
                .execute();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(long vkUserId) {
        dsl.deleteFrom(ATTEMPT).where(USER_ID.eq(vkUserId)).execute();
    }
}
