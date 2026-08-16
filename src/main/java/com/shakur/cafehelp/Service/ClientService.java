package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ClientDTO;
import com.shakur.cafehelp.DTO.ClientDishDTO;
import com.shakur.cafehelp.DTO.ClientWithDutyDTO;
import com.shakur.cafehelp.DTO.DebtPaymentDTO;
import com.shakur.cafehelp.DTO.DebtPaymentRequestDTO;
import com.shakur.cafehelp.DTO.OrderDTO;
import com.shakur.cafehelp.config.BusinessTimeProvider;
import jooqdata.tables.Clientdish;
import jooqdata.tables.Dish;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static jooqdata.tables.Client.CLIENT;
import static jooqdata.tables.Order.ORDER;

@Service
public class ClientService {
    private static final Field<LocalDateTime> ORDER_CANCELLED_AT =
            DSL.field(DSL.name("cancelled_at"), LocalDateTime.class);
    private static final Field<BigDecimal> DEBT_ORIGINAL_AMOUNT =
            DSL.field(DSL.name("debt_original_amount"), BigDecimal.class);
    private static final Field<BigDecimal> DEBT_REMAINING_AMOUNT =
            DSL.field(DSL.name("debt_remaining_amount"), BigDecimal.class);
    private static final Field<Boolean> IS_PAID = DSL.field(DSL.name("is_paid"), Boolean.class);
    private static final Field<String> PAYMENT_TYPE = DSL.field(DSL.name("payment_type"), String.class);
    private static final Field<LocalDateTime> PAID_AT = DSL.field(DSL.name("paid_at"), LocalDateTime.class);
    private static final Field<String> NORMALIZED_NUMBER = DSL.field(DSL.name("normalized_number"), String.class);

    private static final Table<?> DEBT_PAYMENT = DSL.table(DSL.name("sales", "debt_payment"));
    private static final Field<Long> DEBT_PAYMENT_ID = DSL.field(DSL.name("id"), Long.class);
    private static final Field<Integer> DEBT_PAYMENT_ORDER_ID = DSL.field(DSL.name("order_id"), Integer.class);
    private static final Field<Integer> DEBT_PAYMENT_CLIENT_ID = DSL.field(DSL.name("client_id"), Integer.class);
    private static final Field<BigDecimal> DEBT_PAYMENT_AMOUNT = DSL.field(DSL.name("amount"), BigDecimal.class);
    private static final Field<BigDecimal> DEBT_PAYMENT_REMAINING_AFTER = DSL.field(DSL.name("remaining_after"), BigDecimal.class);
    private static final Field<String> DEBT_PAYMENT_TYPE = DSL.field(DSL.name("payment_type"), String.class);
    private static final Field<String> DEBT_PAYMENT_KEY = DSL.field(DSL.name("idempotency_key"), String.class);
    private static final Field<LocalDateTime> DEBT_PAYMENT_CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime.class);

    private final DSLContext dsl;
    private final BusinessTimeProvider businessTime;
    private final OrderService orderService;
    private final TaxOutboxWriterService taxOutboxWriterService;

    public ClientService(
            DSLContext dsl,
            BusinessTimeProvider businessTime,
            OrderService orderService,
            TaxOutboxWriterService taxOutboxWriterService
    ) {
        this.dsl = dsl;
        this.businessTime = businessTime;
        this.orderService = orderService;
        this.taxOutboxWriterService = taxOutboxWriterService;
    }

    public List<ClientDTO> getAllClients() {
        return dsl.selectFrom(CLIENT)
                .orderBy(CLIENT.FULLNAME.asc(), CLIENT.CLIENTID.asc())
                .fetch(this::mapClient);
    }

    public ClientDTO getClientById(int clientId) {
        return dsl.selectFrom(CLIENT)
                .where(CLIENT.CLIENTID.eq(clientId))
                .fetchOne(this::mapClient);
    }

    public List<ClientDTO> searchClients(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            return getAllClients();
        }
        String pattern = "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return dsl.selectFrom(CLIENT)
                .where(CLIENT.FULLNAME.likeIgnoreCase(pattern, '\\')
                        .or(CLIENT.NUMBER.likeIgnoreCase(pattern, '\\')))
                .orderBy(CLIENT.FULLNAME.asc(), CLIENT.CLIENTID.asc())
                .limit(100)
                .fetch(this::mapClient);
    }

    @Transactional
    public ClientDTO createClient(ClientDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Данные клиента обязательны");
        }
        String fullName = normalizeName(dto.getFullName());
        String normalizedPhone = normalizePhone(dto.getNumber());
        String displayPhone = formatPhone(normalizedPhone);

        if (normalizedPhone != null) {
            dsl.fetch("select pg_advisory_xact_lock(hashtext(?))", normalizedPhone);
            boolean duplicate = dsl.fetchExists(
                    dsl.selectOne().from(CLIENT)
                            .where(NORMALIZED_NUMBER.eq(normalizedPhone)
                                    .or(DSL.field("regexp_replace(coalesce({0}, ''), '[^0-9]', '', 'g')", String.class, CLIENT.NUMBER)
                                            .eq(normalizedPhone)))
            );
            if (duplicate) {
                throw new IllegalArgumentException("Клиент с таким телефоном уже существует");
            }
        }

        Integer clientId = dsl.insertInto(CLIENT)
                .set(CLIENT.FULLNAME, fullName)
                .set(CLIENT.NUMBER, displayPhone)
                .set(NORMALIZED_NUMBER, normalizedPhone)
                .returningResult(CLIENT.CLIENTID)
                .fetchOne(CLIENT.CLIENTID);
        if (clientId == null) {
            throw new IllegalStateException("Не удалось создать клиента");
        }

        ClientDTO created = new ClientDTO();
        created.setClientId(clientId);
        created.setFullName(fullName);
        created.setNumber(displayPhone);
        return created;
    }

    public List<ClientWithDutyDTO> getClientsWithDutyOrders(boolean duty) {
        List<ClientDTO> clients = dsl.selectDistinct(CLIENT.CLIENTID, CLIENT.FULLNAME, CLIENT.NUMBER)
                .from(CLIENT)
                .join(ORDER).on(ORDER.CLIENTID.eq(CLIENT.CLIENTID))
                .where(ORDER.DUTY.eq(duty))
                .and(ORDER_CANCELLED_AT.isNull())
                .orderBy(CLIENT.FULLNAME.asc(), CLIENT.CLIENTID.asc())
                .fetch(record -> {
                    ClientDTO client = new ClientDTO();
                    client.setClientId(record.get(CLIENT.CLIENTID));
                    client.setFullName(record.get(CLIENT.FULLNAME));
                    client.setNumber(record.get(CLIENT.NUMBER));
                    return client;
                });

        List<ClientWithDutyDTO> result = new ArrayList<>();
        for (ClientDTO client : clients) {
            List<OrderDTO> orders = dsl.select(
                            ORDER.ORDERID,
                            ORDER.DATE,
                            ORDER.CREATED_AT,
                            ORDER.AMOUNT,
                            ORDER.DUTY,
                            ORDER.TIMEDELAY,
                            ORDER.DEBT_PAYMENT_DATE,
                            DEBT_ORIGINAL_AMOUNT,
                            DEBT_REMAINING_AMOUNT
                    )
                    .from(ORDER)
                    .where(ORDER.CLIENTID.eq(client.getClientId()))
                    .and(ORDER.DUTY.eq(duty))
                    .and(ORDER_CANCELLED_AT.isNull())
                    .orderBy(ORDER.DEBT_PAYMENT_DATE.asc().nullsLast(), ORDER.ORDERID.asc())
                    .fetch(this::mapDebtOrder);
            result.add(new ClientWithDutyDTO(client, orders));
        }
        return result;
    }

    public List<ClientDishDTO> getDishesByClientId(int clientId) {
        return dsl.select()
                .from(Clientdish.CLIENTDISH)
                .join(Dish.DISH).on(Clientdish.CLIENTDISH.DISHID.eq(Dish.DISH.DISHID))
                .where(Clientdish.CLIENTDISH.CLIENTID.eq(clientId))
                .fetch(record -> {
                    ClientDishDTO dto = new ClientDishDTO();
                    dto.clientId = record.get(Clientdish.CLIENTDISH.CLIENTID);
                    dto.dishId = record.get(Dish.DISH.DISHID);
                    dto.dishName = record.get(Dish.DISH.DISHNAME);
                    return dto;
                });
    }

    @Transactional
    public DebtPaymentDTO payDebt(int orderId, DebtPaymentRequestDTO request) {
        return payDebtInternal(orderId, request);
    }

    @Transactional
    public Map<String, Object> deleteDutyByOrderId(int orderId) {
        BigDecimal remaining = currentRemainingAmount(orderId);
        DebtPaymentRequestDTO request = fullPaymentRequest(remaining, "legacy-order-" + orderId + "-" + UUID.randomUUID());
        DebtPaymentDTO payment = payDebtInternal(orderId, request);
        return paymentResultMap(payment, 1);
    }

    @Transactional
    public Map<String, Object> deleteAllDutyByClientId(int clientId) {
        if (!dsl.fetchExists(dsl.selectOne().from(CLIENT).where(CLIENT.CLIENTID.eq(clientId)))) {
            throw new IllegalArgumentException("Клиент не найден");
        }
        List<Integer> orderIds = dsl.select(ORDER.ORDERID)
                .from(ORDER)
                .where(ORDER.CLIENTID.eq(clientId))
                .and(ORDER.DUTY.eq(true))
                .and(ORDER_CANCELLED_AT.isNull())
                .orderBy(ORDER.ORDERID.asc())
                .fetch(ORDER.ORDERID);
        if (orderIds.isEmpty()) {
            throw new IllegalStateException("У клиента нет открытых долгов");
        }

        BigDecimal paidTotal = BigDecimal.ZERO;
        DebtPaymentDTO last = null;
        String operationId = UUID.randomUUID().toString();
        for (Integer orderId : orderIds) {
            BigDecimal remaining = currentRemainingAmount(orderId);
            last = payDebtInternal(orderId, fullPaymentRequest(remaining, "legacy-client-" + operationId + "-" + orderId));
            paidTotal = paidTotal.add(last.amount());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("clientId", clientId);
        result.put("updatedOrdersCount", orderIds.size());
        result.put("totalDutyAmount", paidTotal);
        result.put("message", "Погашено долгов: " + orderIds.size() + " на сумму " + paidTotal + " ₽");
        return result;
    }

    public List<DebtPaymentDTO> getDebtPaymentHistory(int orderId) {
        return dsl.select(
                        DEBT_PAYMENT_ID,
                        DEBT_PAYMENT_ORDER_ID,
                        DEBT_PAYMENT_CLIENT_ID,
                        DEBT_PAYMENT_AMOUNT,
                        DEBT_PAYMENT_REMAINING_AFTER,
                        DEBT_PAYMENT_TYPE,
                        DEBT_PAYMENT_KEY,
                        DEBT_PAYMENT_CREATED_AT
                )
                .from(DEBT_PAYMENT)
                .where(DEBT_PAYMENT_ORDER_ID.eq(orderId))
                .orderBy(DEBT_PAYMENT_CREATED_AT.asc(), DEBT_PAYMENT_ID.asc())
                .fetch(this::mapPayment);
    }

    @Transactional
    public OrderDTO addDutyData(int orderId, LocalDate paymentDate) {
        if (paymentDate == null) {
            throw new IllegalArgumentException("Дата погашения обязательна");
        }
        int updated = dsl.update(ORDER)
                .set(ORDER.DEBT_PAYMENT_DATE, paymentDate)
                .where(ORDER.ORDERID.eq(orderId))
                .and(ORDER.DUTY.eq(true))
                .and(ORDER_CANCELLED_AT.isNull())
                .execute();
        if (updated != 1) {
            throw new IllegalArgumentException("Открытый долг не найден");
        }
        return dsl.select(
                        ORDER.ORDERID,
                        ORDER.DATE,
                        ORDER.CREATED_AT,
                        ORDER.AMOUNT,
                        ORDER.DUTY,
                        ORDER.TIMEDELAY,
                        ORDER.DEBT_PAYMENT_DATE,
                        DEBT_ORIGINAL_AMOUNT,
                        DEBT_REMAINING_AMOUNT
                )
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .fetchOne(this::mapDebtOrder);
    }

    public List<OrderDTO> getDebtsDueToday() {
        return getDebtsByDate(businessTime.today(), false);
    }

    public List<OrderDTO> getOverdueDebts() {
        return getDebtsByDate(businessTime.today(), true);
    }

    private List<OrderDTO> getDebtsByDate(LocalDate today, boolean overdue) {
        var dateCondition = overdue
                ? ORDER.DEBT_PAYMENT_DATE.lt(today)
                : ORDER.DEBT_PAYMENT_DATE.eq(today);
        return dsl.select(
                        ORDER.ORDERID,
                        ORDER.CLIENTID,
                        ORDER.DATE,
                        ORDER.CREATED_AT,
                        ORDER.AMOUNT,
                        ORDER.STATUS,
                        ORDER.DUTY,
                        ORDER.TIMEDELAY,
                        ORDER.DEBT_PAYMENT_DATE,
                        DEBT_ORIGINAL_AMOUNT,
                        DEBT_REMAINING_AMOUNT
                )
                .from(ORDER)
                .where(ORDER.DUTY.eq(true))
                .and(dateCondition)
                .and(ORDER_CANCELLED_AT.isNull())
                .orderBy(ORDER.DEBT_PAYMENT_DATE.asc(), ORDER.ORDERID.asc())
                .fetch(this::mapDebtOrder);
    }

    private DebtPaymentDTO payDebtInternal(int orderId, DebtPaymentRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Данные платежа обязательны");
        }
        String key = normalizeIdempotencyKey(request.getIdempotencyKey());
        Record previous = findPaymentByKey(key);
        if (previous != null) {
            if (!Integer.valueOf(orderId).equals(previous.get(DEBT_PAYMENT_ORDER_ID))) {
                throw new IllegalArgumentException("Ключ идемпотентности уже использован для другого заказа");
            }
            return mapPayment(previous);
        }

        Record debt = dsl.select(
                        ORDER.ORDERID,
                        ORDER.CLIENTID,
                        ORDER.AMOUNT,
                        ORDER.DUTY,
                        ORDER_CANCELLED_AT,
                        DEBT_REMAINING_AMOUNT
                )
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .forUpdate()
                .fetchOne();
        Record concurrentPrevious = findPaymentByKey(key);
        if (concurrentPrevious != null) {
            if (!Integer.valueOf(orderId).equals(concurrentPrevious.get(DEBT_PAYMENT_ORDER_ID))) {
                throw new IllegalArgumentException("Ключ идемпотентности уже использован для другого заказа");
            }
            return mapPayment(concurrentPrevious);
        }
        if (debt == null || debt.get(ORDER_CANCELLED_AT) != null) {
            throw new IllegalArgumentException("Заказ не найден");
        }
        if (!Boolean.TRUE.equals(debt.get(ORDER.DUTY))) {
            throw new IllegalStateException("Долг уже полностью погашен");
        }
        Integer clientId = debt.get(ORDER.CLIENTID);
        if (clientId == null) {
            throw new IllegalStateException("У долга не указан клиент");
        }
        BigDecimal remaining = debt.get(DEBT_REMAINING_AMOUNT);
        if (remaining == null) {
            remaining = money(debt.get(ORDER.AMOUNT));
        }
        BigDecimal amount = money(request.getAmount());
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Сумма платежа должна быть больше нуля");
        }
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Сумма платежа превышает остаток долга");
        }
        String paymentType = normalizePaymentType(request.getPaymentType());
        BigDecimal nextRemaining = remaining.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        boolean fullyPaid = nextRemaining.signum() == 0;
        LocalDateTime createdAt = businessTime.now();

        Long paymentId = dsl.insertInto(DEBT_PAYMENT)
                .columns(
                        DEBT_PAYMENT_ORDER_ID,
                        DEBT_PAYMENT_CLIENT_ID,
                        DEBT_PAYMENT_AMOUNT,
                        DEBT_PAYMENT_REMAINING_AFTER,
                        DEBT_PAYMENT_TYPE,
                        DEBT_PAYMENT_KEY,
                        DEBT_PAYMENT_CREATED_AT
                )
                .values(orderId, clientId, amount, nextRemaining, paymentType, key, createdAt)
                .returningResult(DEBT_PAYMENT_ID)
                .fetchOne(DEBT_PAYMENT_ID);

        dsl.update(ORDER)
                .set(DEBT_REMAINING_AMOUNT, nextRemaining)
                .set(ORDER.DUTY, !fullyPaid)
                .set(IS_PAID, fullyPaid)
                .set(PAYMENT_TYPE, fullyPaid ? paymentType : "unpaid")
                .set(PAID_AT, fullyPaid ? createdAt : null)
                .where(ORDER.ORDERID.eq(orderId))
                .execute();

        if (fullyPaid) {
            taxOutboxWriterService.enqueuePaidOrder(
                    orderId,
                    orderService.getOrderKitchenPrintPayload(orderId, null, null, null, null),
                    "order_paid",
                    "debt-payment",
                    false,
                    null
            );
        }

        return new DebtPaymentDTO(
                paymentId,
                orderId,
                clientId,
                amount,
                nextRemaining,
                paymentType,
                key,
                createdAt,
                fullyPaid
        );
    }

    private BigDecimal currentRemainingAmount(int orderId) {
        Record record = dsl.select(ORDER.AMOUNT, ORDER.DUTY, DEBT_REMAINING_AMOUNT)
                .from(ORDER)
                .where(ORDER.ORDERID.eq(orderId))
                .and(ORDER_CANCELLED_AT.isNull())
                .fetchOne();
        if (record == null) {
            throw new IllegalArgumentException("Заказ не найден");
        }
        if (!Boolean.TRUE.equals(record.get(ORDER.DUTY))) {
            throw new IllegalStateException("Долг уже полностью погашен");
        }
        BigDecimal remaining = record.get(DEBT_REMAINING_AMOUNT);
        return remaining != null ? remaining : money(record.get(ORDER.AMOUNT));
    }

    private DebtPaymentDTO mapPayment(Record record) {
        BigDecimal remaining = record.get(DEBT_PAYMENT_REMAINING_AFTER);
        return new DebtPaymentDTO(
                record.get(DEBT_PAYMENT_ID),
                record.get(DEBT_PAYMENT_ORDER_ID),
                record.get(DEBT_PAYMENT_CLIENT_ID),
                record.get(DEBT_PAYMENT_AMOUNT),
                remaining,
                record.get(DEBT_PAYMENT_TYPE),
                record.get(DEBT_PAYMENT_KEY),
                record.get(DEBT_PAYMENT_CREATED_AT),
                remaining.signum() == 0
        );
    }

    private Record findPaymentByKey(String key) {
        return dsl.select(
                        DEBT_PAYMENT_ID,
                        DEBT_PAYMENT_ORDER_ID,
                        DEBT_PAYMENT_CLIENT_ID,
                        DEBT_PAYMENT_AMOUNT,
                        DEBT_PAYMENT_REMAINING_AFTER,
                        DEBT_PAYMENT_TYPE,
                        DEBT_PAYMENT_KEY,
                        DEBT_PAYMENT_CREATED_AT
                )
                .from(DEBT_PAYMENT)
                .where(DEBT_PAYMENT_KEY.eq(key))
                .fetchOne();
    }

    private Map<String, Object> paymentResultMap(DebtPaymentDTO payment, int count) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("orderId", payment.orderId());
        result.put("clientId", payment.clientId());
        result.put("amount", payment.amount());
        result.put("remainingAmount", payment.remainingAmount());
        result.put("updatedOrdersCount", count);
        result.put("message", "Платёж по долгу принят");
        return result;
    }

    private DebtPaymentRequestDTO fullPaymentRequest(BigDecimal remaining, String key) {
        DebtPaymentRequestDTO request = new DebtPaymentRequestDTO();
        request.setAmount(remaining);
        request.setPaymentType("cash");
        request.setIdempotencyKey(key);
        return request;
    }

    private ClientDTO mapClient(Record record) {
        ClientDTO dto = new ClientDTO();
        dto.setClientId(record.get(CLIENT.CLIENTID));
        dto.setFullName(record.get(CLIENT.FULLNAME));
        dto.setNumber(record.get(CLIENT.NUMBER));
        return dto;
    }

    private OrderDTO mapDebtOrder(Record record) {
        OrderDTO dto = new OrderDTO();
        dto.setOrderId(record.get(ORDER.ORDERID));
        dto.setClientId(record.get(ORDER.CLIENTID));
        dto.setDate(record.get(ORDER.DATE));
        dto.setCreated_at(record.get(ORDER.CREATED_AT));
        dto.setStatus(record.get(ORDER.STATUS));
        dto.setDuty(record.get(ORDER.DUTY));
        dto.setTimeDelay(record.get(ORDER.TIMEDELAY));
        dto.setDebt_payment_date(record.get(ORDER.DEBT_PAYMENT_DATE));
        BigDecimal original = record.get(DEBT_ORIGINAL_AMOUNT);
        BigDecimal remaining = record.get(DEBT_REMAINING_AMOUNT);
        dto.setDebtOriginalAmount(original);
        dto.setDebtRemainingAmount(remaining);
        dto.setAmount(remaining != null ? remaining.doubleValue() : record.get(ORDER.AMOUNT));
        return dto;
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Имя клиента обязательно");
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 255) {
            throw new IllegalArgumentException("Имя клиента слишком длинное");
        }
        return normalized;
    }

    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("8")) {
            digits = "7" + digits.substring(1);
        } else if (digits.length() == 10) {
            digits = "7" + digits;
        }
        if (digits.length() < 11 || digits.length() > 15) {
            throw new IllegalArgumentException("Некорректный номер телефона");
        }
        return digits;
    }

    private String formatPhone(String normalized) {
        if (normalized == null) {
            return null;
        }
        if (normalized.length() == 11 && normalized.startsWith("7")) {
            return "+7 " + normalized.substring(1, 4) + " " + normalized.substring(4, 7)
                    + "-" + normalized.substring(7, 9) + "-" + normalized.substring(9);
        }
        return "+" + normalized;
    }

    private String normalizePaymentType(String value) {
        String normalized = value == null ? "cash" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("cash", "transfer", "card").contains(normalized)) {
            throw new IllegalArgumentException("Допустимы наличные, перевод или карта");
        }
        return normalized;
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Ключ идемпотентности обязателен");
        }
        String normalized = value.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("Ключ идемпотентности слишком длинный");
        }
        return normalized;
    }

    private BigDecimal money(Number value) {
        if (value == null) {
            throw new IllegalArgumentException("Сумма платежа обязательна");
        }
        BigDecimal amount = value instanceof BigDecimal decimal
                ? decimal
                : BigDecimal.valueOf(value.doubleValue());
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
