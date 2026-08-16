package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.SalaryPaymentDTO;
import com.shakur.cafehelp.DTO.SalaryPaymentPageDTO;
import com.shakur.cafehelp.DTO.SalaryPaymentRequestDTO;
import com.shakur.cafehelp.DTO.SalaryReversalRequestDTO;
import com.shakur.cafehelp.DTO.SalarySummaryDTO;
import com.shakur.cafehelp.config.BusinessTimeProvider;
import jooqdata.tables.Person;
import jooqdata.tables.Shift;
import jooqdata.tables.Shiftperson;
import jooqdata.tables.UserAccount;
import jooqdata.tables.records.PersonRecord;
import jooqdata.tables.records.ShiftRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class PayrollService {
    private static final Table<?> SALARY_ACCRUAL = DSL.table(DSL.name("sales", "salary_accrual"));
    private static final Field<Long> ACCRUAL_ID = DSL.field(DSL.name("id"), Long.class);
    private static final Field<Integer> ACCRUAL_PERSON_ID = DSL.field(DSL.name("person_id"), Integer.class);
    private static final Field<Integer> ACCRUAL_SHIFT_ID = DSL.field(DSL.name("shift_id"), Integer.class);
    private static final Field<LocalDate> ACCRUAL_WORKED_ON = DSL.field(DSL.name("worked_on"), LocalDate.class);
    private static final Field<BigDecimal> ACCRUAL_DAILY_RATE = DSL.field(DSL.name("daily_rate"), BigDecimal.class);
    private static final Field<BigDecimal> ACCRUAL_AMOUNT = DSL.field(DSL.name("amount"), BigDecimal.class);
    private static final Field<LocalDateTime> ACCRUAL_CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime.class);

    private static final Table<?> SALARY_PAYMENT = DSL.table(DSL.name("sales", "salary_payment"));
    private static final Field<Long> PAYMENT_ID = DSL.field(DSL.name("id"), Long.class);
    private static final Field<Integer> PAYMENT_PERSON_ID = DSL.field(DSL.name("person_id"), Integer.class);
    private static final Field<String> PAYMENT_ENTRY_TYPE = DSL.field(DSL.name("entry_type"), String.class);
    private static final Field<BigDecimal> PAYMENT_AMOUNT = DSL.field(DSL.name("amount"), BigDecimal.class);
    private static final Field<BigDecimal> PAYMENT_BALANCE_AFTER = DSL.field(DSL.name("balance_after"), BigDecimal.class);
    private static final Field<String> PAYMENT_IDEMPOTENCY_KEY = DSL.field(DSL.name("idempotency_key"), String.class);
    private static final Field<Integer> PAYMENT_AUTHOR_ACCOUNT_ID = DSL.field(DSL.name("author_account_id"), Integer.class);
    private static final Field<Long> PAYMENT_RELATED_ID = DSL.field(DSL.name("related_payment_id"), Long.class);
    private static final Field<String> PAYMENT_COMMENT = DSL.field(DSL.name("comment"), String.class);
    private static final Field<LocalDateTime> PAYMENT_CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime.class);

    private static final Field<Boolean> PERSON_ARCHIVED = DSL.field(DSL.name("archived"), Boolean.class);

    private final DSLContext dsl;
    private final BusinessTimeProvider businessTime;

    public PayrollService(DSLContext dsl, BusinessTimeProvider businessTime) {
        this.dsl = dsl;
        this.businessTime = businessTime;
    }

    @Transactional
    public void accrueClosedShift(int shiftId) {
        ShiftRecord shift = dsl.selectFrom(Shift.SHIFT)
                .where(Shift.SHIFT.ID.eq(shiftId))
                .fetchOne();
        if (shift == null || shift.getEndtime() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Начисление возможно только для закрытой смены");
        }

        LinkedHashSet<Integer> personIds = new LinkedHashSet<>();
        if (shift.getPersoncode() != null) {
            personIds.add(shift.getPersoncode());
        }
        personIds.addAll(dsl.select(Shiftperson.SHIFTPERSON.PERSONID)
                .from(Shiftperson.SHIFTPERSON)
                .where(Shiftperson.SHIFTPERSON.SHIFTID.eq(shiftId))
                .and(Shiftperson.SHIFTPERSON.PERSONID.isNotNull())
                .fetch(Shiftperson.SHIFTPERSON.PERSONID));

        if (personIds.isEmpty()) {
            return;
        }

        List<PersonRecord> people = dsl.selectFrom(Person.PERSON)
                .where(Person.PERSON.PERSONID.in(personIds))
                .orderBy(Person.PERSON.PERSONID.asc())
                .forUpdate()
                .fetch();
        if (people.size() != personIds.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Один из сотрудников смены больше не существует");
        }

        LocalDate workedOn = shift.getData() != null ? shift.getData() : businessTime.today();
        LocalDateTime createdAt = businessTime.now();
        for (PersonRecord person : people) {
            BigDecimal dailyRate = money(person.getSalaryperday());
            dsl.insertInto(SALARY_ACCRUAL)
                    .columns(
                            ACCRUAL_PERSON_ID,
                            ACCRUAL_SHIFT_ID,
                            ACCRUAL_WORKED_ON,
                            ACCRUAL_DAILY_RATE,
                            ACCRUAL_AMOUNT,
                            ACCRUAL_CREATED_AT
                    )
                    .values(person.getPersonid(), shiftId, workedOn, dailyRate, dailyRate, createdAt)
                    .onConflict(ACCRUAL_PERSON_ID, ACCRUAL_SHIFT_ID)
                    .doNothing()
                    .execute();
        }
    }

    public List<SalarySummaryDTO> getSummaries() {
        return dsl.selectFrom(Person.PERSON)
                .where(PERSON_ARCHIVED.eq(false))
                .orderBy(Person.PERSON.NAME.asc(), Person.PERSON.PERSONID.asc())
                .fetch(person -> summaryFor(person));
    }

    public BigDecimal getOutstandingBalance(int personId) {
        ensurePersonExists(personId);
        return currentBalance(personId);
    }

    public SalaryPaymentPageDTO getPaymentHistory(int personId, int page, int size) {
        ensurePersonExists(personId);
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.min(100, Math.max(1, size));
        Condition condition = PAYMENT_PERSON_ID.eq(personId);
        long total = dsl.fetchCount(SALARY_PAYMENT, condition);
        List<SalaryPaymentDTO> items = dsl.select(
                        PAYMENT_ID,
                        PAYMENT_PERSON_ID,
                        PAYMENT_ENTRY_TYPE,
                        PAYMENT_AMOUNT,
                        PAYMENT_BALANCE_AFTER,
                        PAYMENT_IDEMPOTENCY_KEY,
                        PAYMENT_AUTHOR_ACCOUNT_ID,
                        PAYMENT_RELATED_ID,
                        PAYMENT_COMMENT,
                        PAYMENT_CREATED_AT
                )
                .from(SALARY_PAYMENT)
                .where(condition)
                .orderBy(PAYMENT_CREATED_AT.desc(), PAYMENT_ID.desc())
                .limit(normalizedSize)
                .offset(normalizedPage * normalizedSize)
                .fetch(this::mapPayment);
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) normalizedSize);
        return new SalaryPaymentPageDTO(items, normalizedPage, normalizedSize, total, totalPages);
    }

    @Transactional
    public SalaryPaymentDTO createPayment(int personId, SalaryPaymentRequestDTO request, String username) {
        if (request == null) {
            throw badRequest("Данные выплаты обязательны");
        }
        BigDecimal amount = positiveMoney(request.getAmount(), "Сумма выплаты должна быть больше нуля");
        String key = normalizeIdempotencyKey(request.getIdempotencyKey());
        String comment = normalizeComment(request.getComment(), false);
        Author author = requireOwner(username);

        Record previous = findByIdempotencyKey(key);
        if (previous != null) {
            return validateRepeatedPayment(previous, personId, amount);
        }

        lockPerson(personId);
        previous = findByIdempotencyKey(key);
        if (previous != null) {
            return validateRepeatedPayment(previous, personId, amount);
        }

        BigDecimal balance = currentBalance(personId);
        if (amount.compareTo(balance) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Сумма выплаты превышает доступный остаток " + balance + " ₽");
        }
        BigDecimal nextBalance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        Long paymentId = dsl.insertInto(SALARY_PAYMENT)
                .columns(
                        PAYMENT_PERSON_ID,
                        PAYMENT_ENTRY_TYPE,
                        PAYMENT_AMOUNT,
                        PAYMENT_BALANCE_AFTER,
                        PAYMENT_IDEMPOTENCY_KEY,
                        PAYMENT_AUTHOR_ACCOUNT_ID,
                        PAYMENT_COMMENT,
                        PAYMENT_CREATED_AT
                )
                .values(
                        personId,
                        "PAYMENT",
                        amount,
                        nextBalance,
                        key,
                        author.accountId(),
                        comment,
                        businessTime.now()
                )
                .returningResult(PAYMENT_ID)
                .fetchOne(PAYMENT_ID);
        return getPayment(paymentId);
    }

    @Transactional
    public SalaryPaymentDTO reversePayment(long paymentId, SalaryReversalRequestDTO request, String username) {
        if (request == null) {
            throw badRequest("Данные отмены обязательны");
        }
        String key = normalizeIdempotencyKey(request.getIdempotencyKey());
        String comment = normalizeComment(request.getComment(), true);
        Author author = requireOwner(username);

        Record previous = findByIdempotencyKey(key);
        if (previous != null) {
            return validateRepeatedReversal(previous, paymentId);
        }

        Record original = findPayment(paymentId);
        if (original == null || !"PAYMENT".equals(original.get(PAYMENT_ENTRY_TYPE))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Исходная выплата не найдена");
        }
        int personId = original.get(PAYMENT_PERSON_ID);
        lockPerson(personId);

        previous = findByIdempotencyKey(key);
        if (previous != null) {
            return validateRepeatedReversal(previous, paymentId);
        }
        original = findPaymentForUpdate(paymentId);
        boolean alreadyReversed = dsl.fetchExists(
                dsl.selectOne()
                        .from(SALARY_PAYMENT)
                        .where(PAYMENT_ENTRY_TYPE.eq("REVERSAL"))
                        .and(PAYMENT_RELATED_ID.eq(paymentId))
        );
        if (alreadyReversed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Выплата уже отменена");
        }

        BigDecimal amount = money(original.get(PAYMENT_AMOUNT));
        BigDecimal nextBalance = currentBalance(personId).add(amount).setScale(2, RoundingMode.HALF_UP);
        Long reversalId = dsl.insertInto(SALARY_PAYMENT)
                .columns(
                        PAYMENT_PERSON_ID,
                        PAYMENT_ENTRY_TYPE,
                        PAYMENT_AMOUNT,
                        PAYMENT_BALANCE_AFTER,
                        PAYMENT_IDEMPOTENCY_KEY,
                        PAYMENT_AUTHOR_ACCOUNT_ID,
                        PAYMENT_RELATED_ID,
                        PAYMENT_COMMENT,
                        PAYMENT_CREATED_AT
                )
                .values(
                        personId,
                        "REVERSAL",
                        amount,
                        nextBalance,
                        key,
                        author.accountId(),
                        paymentId,
                        comment,
                        businessTime.now()
                )
                .returningResult(PAYMENT_ID)
                .fetchOne(PAYMENT_ID);
        return getPayment(reversalId);
    }

    private SalarySummaryDTO summaryFor(PersonRecord person) {
        Record accrual = dsl.select(
                        DSL.count(ACCRUAL_ID).as("shift_count"),
                        DSL.coalesce(DSL.sum(ACCRUAL_AMOUNT), BigDecimal.ZERO).as("accrued_amount")
                )
                .from(SALARY_ACCRUAL)
                .where(ACCRUAL_PERSON_ID.eq(person.getPersonid()))
                .fetchOne();
        Record payment = dsl.fetchOne(
                """
                select
                    coalesce(sum(case when entry_type = 'PAYMENT' then amount else -amount end), 0) as paid_amount,
                    max(created_at) filter (where entry_type = 'PAYMENT') as last_paid_at
                from sales.salary_payment
                where person_id = ?
                """,
                person.getPersonid()
        );
        int shifts = accrual != null ? accrual.get("shift_count", Integer.class) : 0;
        BigDecimal accrued = accrual != null ? money(accrual.get("accrued_amount", BigDecimal.class)) : money(null);
        BigDecimal paid = payment != null ? money(payment.get("paid_amount", BigDecimal.class)) : money(null);
        BigDecimal balance = accrued.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        LocalDateTime lastPaidAt = payment != null ? payment.get("last_paid_at", LocalDateTime.class) : null;
        return new SalarySummaryDTO(
                person.getPersonid(),
                person.getName(),
                money(person.getSalaryperday()),
                shifts,
                accrued,
                paid,
                balance,
                lastPaidAt
        );
    }

    private BigDecimal currentBalance(int personId) {
        BigDecimal accrued = dsl.select(DSL.coalesce(DSL.sum(ACCRUAL_AMOUNT), BigDecimal.ZERO))
                .from(SALARY_ACCRUAL)
                .where(ACCRUAL_PERSON_ID.eq(personId))
                .fetchOne(0, BigDecimal.class);
        Record payment = dsl.fetchOne(
                """
                select coalesce(sum(case when entry_type = 'PAYMENT' then amount else -amount end), 0) as paid_amount
                from sales.salary_payment
                where person_id = ?
                """,
                personId
        );
        BigDecimal paid = payment != null ? money(payment.get("paid_amount", BigDecimal.class)) : money(null);
        return money(accrued).subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private Record findPayment(long paymentId) {
        return paymentSelect().where(PAYMENT_ID.eq(paymentId)).fetchOne();
    }

    private Record findPaymentForUpdate(long paymentId) {
        Record result = paymentSelect().where(PAYMENT_ID.eq(paymentId)).forUpdate().fetchOne();
        if (result == null || !"PAYMENT".equals(result.get(PAYMENT_ENTRY_TYPE))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Исходная выплата не найдена");
        }
        return result;
    }

    private Record findByIdempotencyKey(String key) {
        return paymentSelect().where(PAYMENT_IDEMPOTENCY_KEY.eq(key)).fetchOne();
    }

    private org.jooq.SelectJoinStep<? extends Record> paymentSelect() {
        return dsl.select(
                        PAYMENT_ID,
                        PAYMENT_PERSON_ID,
                        PAYMENT_ENTRY_TYPE,
                        PAYMENT_AMOUNT,
                        PAYMENT_BALANCE_AFTER,
                        PAYMENT_IDEMPOTENCY_KEY,
                        PAYMENT_AUTHOR_ACCOUNT_ID,
                        PAYMENT_RELATED_ID,
                        PAYMENT_COMMENT,
                        PAYMENT_CREATED_AT
                )
                .from(SALARY_PAYMENT);
    }

    private SalaryPaymentDTO getPayment(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить проводку");
        }
        Record payment = findPayment(id);
        if (payment == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Сохранённая проводка не найдена");
        }
        return mapPayment(payment);
    }

    private SalaryPaymentDTO validateRepeatedPayment(Record previous, int personId, BigDecimal amount) {
        if (!"PAYMENT".equals(previous.get(PAYMENT_ENTRY_TYPE))
                || !Integer.valueOf(personId).equals(previous.get(PAYMENT_PERSON_ID))
                || money(previous.get(PAYMENT_AMOUNT)).compareTo(amount) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ключ идемпотентности уже использован с другими параметрами");
        }
        return mapPayment(previous);
    }

    private SalaryPaymentDTO validateRepeatedReversal(Record previous, long paymentId) {
        if (!"REVERSAL".equals(previous.get(PAYMENT_ENTRY_TYPE))
                || !Long.valueOf(paymentId).equals(previous.get(PAYMENT_RELATED_ID))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ключ идемпотентности уже использован с другими параметрами");
        }
        return mapPayment(previous);
    }

    private SalaryPaymentDTO mapPayment(Record record) {
        int authorAccountId = record.get(PAYMENT_AUTHOR_ACCOUNT_ID);
        return new SalaryPaymentDTO(
                record.get(PAYMENT_ID),
                record.get(PAYMENT_PERSON_ID),
                record.get(PAYMENT_ENTRY_TYPE),
                money(record.get(PAYMENT_AMOUNT)),
                money(record.get(PAYMENT_BALANCE_AFTER)),
                record.get(PAYMENT_IDEMPOTENCY_KEY),
                authorAccountId,
                resolveAuthorName(authorAccountId),
                record.get(PAYMENT_RELATED_ID),
                record.get(PAYMENT_COMMENT),
                record.get(PAYMENT_CREATED_AT)
        );
    }

    private String resolveAuthorName(int accountId) {
        Record author = dsl.select(UserAccount.USER_ACCOUNT.USERNAME, Person.PERSON.NAME)
                .from(UserAccount.USER_ACCOUNT)
                .leftJoin(Person.PERSON)
                .on(Person.PERSON.PERSONID.eq(UserAccount.USER_ACCOUNT.PERSONID))
                .where(UserAccount.USER_ACCOUNT.ID.eq(accountId))
                .fetchOne();
        if (author == null) {
            return "Аккаунт #" + accountId;
        }
        String personName = author.get(Person.PERSON.NAME);
        return personName != null && !personName.isBlank()
                ? personName
                : author.get(UserAccount.USER_ACCOUNT.USERNAME);
    }

    private Author requireOwner(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется авторизация");
        }
        Record account = dsl.select(
                        UserAccount.USER_ACCOUNT.ID,
                        UserAccount.USER_ACCOUNT.ROLE,
                        UserAccount.USER_ACCOUNT.IS_ACTIVE
                )
                .from(UserAccount.USER_ACCOUNT)
                .where(UserAccount.USER_ACCOUNT.USERNAME.eq(username))
                .fetchOne();
        if (account == null
                || !Boolean.TRUE.equals(account.get(UserAccount.USER_ACCOUNT.IS_ACTIVE))
                || !"OWNER".equals(account.get(UserAccount.USER_ACCOUNT.ROLE))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Выплату может проводить только активный владелец");
        }
        return new Author(account.get(UserAccount.USER_ACCOUNT.ID));
    }

    private void lockPerson(int personId) {
        PersonRecord person = dsl.selectFrom(Person.PERSON)
                .where(Person.PERSON.PERSONID.eq(personId))
                .and(PERSON_ARCHIVED.eq(false))
                .forUpdate()
                .fetchOne();
        if (person == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Сотрудник не найден");
        }
    }

    private void ensurePersonExists(int personId) {
        boolean exists = dsl.fetchExists(
                dsl.selectOne().from(Person.PERSON).where(Person.PERSON.PERSONID.eq(personId))
        );
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Сотрудник не найден");
        }
    }

    private BigDecimal positiveMoney(BigDecimal value, String message) {
        BigDecimal result = money(value);
        if (result.signum() <= 0) {
            throw badRequest(message);
        }
        return result;
    }

    private BigDecimal money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeIdempotencyKey(String value) {
        String key = value != null ? value.trim() : "";
        if (key.length() < 8 || key.length() > 100) {
            throw badRequest("Ключ идемпотентности должен содержать от 8 до 100 символов");
        }
        return key;
    }

    private String normalizeComment(String value, boolean required) {
        String comment = value != null ? value.trim() : "";
        if (required && comment.isEmpty()) {
            throw badRequest("Укажите причину отмены выплаты");
        }
        if (comment.length() > 500) {
            throw badRequest("Комментарий не должен превышать 500 символов");
        }
        return comment.isEmpty() ? null : comment;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record Author(int accountId) {
    }
}
