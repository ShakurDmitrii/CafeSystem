package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.OrderDTO;
import com.shakur.cafehelp.DTO.VkBotLinkConfirmRequestDTO;
import com.shakur.cafehelp.config.BusinessTimeProvider;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VkClientLinkService {
    private static final Table<?> CLIENT = DSL.table(DSL.name("sales", "client"));
    private static final Field<Integer> CLIENT_ID = DSL.field(DSL.name("clientid"), Integer.class);
    private static final Field<String> CLIENT_FULL_NAME = DSL.field(DSL.name("fullname"), String.class);

    private static final Table<?> VK_LINK = DSL.table(DSL.name("sales", "client_vk_link"));
    private static final Field<Long> VK_LINK_ID = DSL.field(DSL.name("id"), Long.class);
    private static final Field<Integer> VK_LINK_CLIENT_ID = DSL.field(DSL.name("client_id"), Integer.class);
    private static final Field<Long> VK_LINK_USER_ID = DSL.field(DSL.name("vk_user_id"), Long.class);
    private static final Field<String> VK_LINK_DOMAIN = DSL.field(DSL.name("vk_domain"), String.class);
    private static final Field<LocalDateTime> VK_LINK_VERIFIED_AT = DSL.field(DSL.name("verified_at"), LocalDateTime.class);
    private static final Field<LocalDateTime> VK_LINK_CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime.class);

    private static final Table<?> VK_CODE = DSL.table(DSL.name("sales", "client_vk_link_code"));
    private static final Field<Long> VK_CODE_ID = DSL.field(DSL.name("id"), Long.class);
    private static final Field<Integer> VK_CODE_CLIENT_ID = DSL.field(DSL.name("client_id"), Integer.class);
    private static final Field<String> VK_CODE_HASH = DSL.field(DSL.name("code_hash"), String.class);
    private static final Field<LocalDateTime> VK_CODE_EXPIRES_AT = DSL.field(DSL.name("expires_at"), LocalDateTime.class);
    private static final Field<LocalDateTime> VK_CODE_USED_AT = DSL.field(DSL.name("used_at"), LocalDateTime.class);
    private static final Field<LocalDateTime> VK_CODE_CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime.class);
    private static final Field<String> VK_CODE_FINGERPRINT = DSL.field(DSL.name("code_fingerprint"), String.class);

    private static final Table<?> VK_EVENT = DSL.table(DSL.name("sales", "client_vk_link_event"));
    private static final Field<Integer> VK_EVENT_CLIENT_ID = DSL.field(DSL.name("client_id"), Integer.class);
    private static final Field<Long> VK_EVENT_USER_ID = DSL.field(DSL.name("vk_user_id"), Long.class);
    private static final Field<String> VK_EVENT_TYPE = DSL.field(DSL.name("event_type"), String.class);
    private static final Field<String> VK_EVENT_DOMAIN = DSL.field(DSL.name("vk_domain"), String.class);
    private static final Field<LocalDateTime> VK_EVENT_CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime.class);

    private final DSLContext dsl;
    private final BCryptPasswordEncoder passwordEncoder;
    private final OrderService orderService;
    private final BusinessTimeProvider businessTime;
    private final VkLinkAttemptService attemptService;
    private final byte[] codePepper;
    private final SecureRandom random = new SecureRandom();

    public VkClientLinkService(
            DSLContext dsl,
            BCryptPasswordEncoder passwordEncoder,
            OrderService orderService,
            BusinessTimeProvider businessTime,
            VkLinkAttemptService attemptService,
            @Value("${security.jwt.secret}") String codePepper
    ) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
        this.orderService = orderService;
        this.businessTime = businessTime;
        this.attemptService = attemptService;
        this.codePepper = codePepper.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public LinkCodeResult createLinkCode(int clientId) {
        if (clientId <= 0 || !clientExists(clientId)) {
            throw new IllegalArgumentException("Client not found");
        }

        LocalDateTime now = businessTime.now();
        dsl.update(VK_CODE)
                .set(VK_CODE_USED_AT, now)
                .where(VK_CODE_CLIENT_ID.eq(clientId))
                .and(VK_CODE_USED_AT.isNull())
                .execute();

        String code = generateCode();
        LocalDateTime expiresAt = now.plusMinutes(15);
        Long codeId = dsl.insertInto(VK_CODE)
                .columns(VK_CODE_CLIENT_ID, VK_CODE_HASH, VK_CODE_FINGERPRINT, VK_CODE_EXPIRES_AT, VK_CODE_CREATED_AT)
                .values(clientId, passwordEncoder.encode(code), fingerprint(code), expiresAt, now)
                .returningResult(VK_CODE_ID)
                .fetchOne(VK_CODE_ID);

        return new LinkCodeResult(codeId, clientId, code, expiresAt);
    }

    @Transactional
    public LinkConfirmResult confirmLink(VkBotLinkConfirmRequestDTO request) {
        if (request == null || request.getVkUserId() == null || request.getVkUserId() <= 0) {
            throw new IllegalArgumentException("vkUserId is required");
        }
        String code = normalizeCode(request.getCode());
        if (code == null) {
            throw new IllegalArgumentException("code is required");
        }

        long vkUserId = request.getVkUserId();
        attemptService.ensureAllowed(vkUserId);
        LocalDateTime now = businessTime.now();
        var candidate = dsl.select(VK_CODE_ID, VK_CODE_CLIENT_ID, VK_CODE_HASH, VK_CODE_EXPIRES_AT)
                .from(VK_CODE)
                .where(VK_CODE_USED_AT.isNull())
                .and(VK_CODE_EXPIRES_AT.gt(now))
                .and(VK_CODE_FINGERPRINT.eq(fingerprint(code)))
                .forUpdate()
                .fetchOne();

        String hash = candidate != null ? candidate.get(VK_CODE_HASH) : null;
        if (candidate == null || hash == null || !passwordEncoder.matches(code, hash)) {
            attemptService.registerFailure(vkUserId);
            throw new IllegalArgumentException("Invalid or expired code");
        }

        int claimed = dsl.update(VK_CODE)
                .set(VK_CODE_USED_AT, now)
                .where(VK_CODE_ID.eq(candidate.get(VK_CODE_ID)))
                .and(VK_CODE_USED_AT.isNull())
                .and(VK_CODE_EXPIRES_AT.gt(now))
                .execute();
        if (claimed != 1) {
            attemptService.registerFailure(vkUserId);
            throw new IllegalArgumentException("Invalid or expired code");
        }

        Integer clientId = candidate.get(VK_CODE_CLIENT_ID);
        upsertLink(clientId, vkUserId, request.getVkDomain(), now);
        attemptService.clear(vkUserId);
        return new LinkConfirmResult(true, clientId, vkUserId, findClientName(clientId));
    }

    @Transactional
    public boolean unlink(Long vkUserId) {
        if (vkUserId == null || vkUserId <= 0) {
            return false;
        }
        var current = dsl.select(VK_LINK_CLIENT_ID, VK_LINK_DOMAIN)
                .from(VK_LINK)
                .where(VK_LINK_USER_ID.eq(vkUserId))
                .forUpdate()
                .fetchOne();
        if (current == null) {
            return false;
        }
        boolean deleted = dsl.deleteFrom(VK_LINK)
                .where(VK_LINK_USER_ID.eq(vkUserId))
                .execute() > 0;
        if (deleted) {
            recordLinkEvent(current.get(VK_LINK_CLIENT_ID), vkUserId, "UNLINKED", current.get(VK_LINK_DOMAIN), businessTime.now());
        }
        return deleted;
    }

    public List<OrderDTO> getOrderHistory(Long vkUserId, Integer limit) {
        Integer clientId = resolveClientId(vkUserId);
        int effectiveLimit = limit != null && limit > 0 ? Math.min(limit, 20) : 5;

        return orderService.getOrdersByClientId(clientId).stream()
                .sorted(Comparator.comparing(this::orderSortDate).reversed())
                .limit(effectiveLimit)
                .map(order -> orderService.getOrderById(order.getOrderId()))
                .toList();
    }

    public OrderDTO getLatestOrder(Long vkUserId) {
        List<OrderDTO> orders = getOrderHistory(vkUserId, 1);
        return orders.isEmpty() ? null : orders.get(0);
    }

    public List<OrderDTO> getDebtOrders(Long vkUserId) {
        Integer clientId = resolveClientId(vkUserId);

        return orderService.getOrdersByClientId(clientId).stream()
                .filter(order -> Boolean.TRUE.equals(order.getDuty()))
                .sorted(Comparator.comparing(this::orderSortDate).reversed())
                .limit(20)
                .map(order -> orderService.getOrderById(order.getOrderId()))
                .toList();
    }

    public Map<String, Object> getLinkStatus(Long vkUserId) {
        if (vkUserId == null || vkUserId <= 0) {
            return Map.of("linked", false);
        }
        var record = dsl.select(VK_LINK_CLIENT_ID, CLIENT_FULL_NAME)
                .from(VK_LINK)
                .join(CLIENT).on(CLIENT_ID.eq(VK_LINK_CLIENT_ID))
                .where(VK_LINK_USER_ID.eq(vkUserId))
                .fetchOne();
        if (record == null) {
            return Map.of("linked", false);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("linked", true);
        response.put("clientId", record.get(VK_LINK_CLIENT_ID));
        response.put("clientName", record.get(CLIENT_FULL_NAME));
        return response;
    }

    private void upsertLink(Integer clientId, Long vkUserId, String vkDomain, LocalDateTime now) {
        var previous = dsl.select(VK_LINK_CLIENT_ID, VK_LINK_USER_ID, VK_LINK_DOMAIN)
                .from(VK_LINK)
                .where(VK_LINK_CLIENT_ID.eq(clientId).or(VK_LINK_USER_ID.eq(vkUserId)))
                .forUpdate()
                .fetch();
        for (var oldLink : previous) {
            recordLinkEvent(
                    oldLink.get(VK_LINK_CLIENT_ID),
                    oldLink.get(VK_LINK_USER_ID),
                    "UNLINKED",
                    oldLink.get(VK_LINK_DOMAIN),
                    now
            );
        }
        dsl.deleteFrom(VK_LINK)
                .where(VK_LINK_CLIENT_ID.eq(clientId).or(VK_LINK_USER_ID.eq(vkUserId)))
                .execute();

        dsl.insertInto(VK_LINK)
                .columns(VK_LINK_CLIENT_ID, VK_LINK_USER_ID, VK_LINK_DOMAIN, VK_LINK_VERIFIED_AT, VK_LINK_CREATED_AT)
                .values(clientId, vkUserId, normalizeDomain(vkDomain), now, now)
                .execute();
        recordLinkEvent(clientId, vkUserId, previous.isEmpty() ? "LINKED" : "RELINKED", normalizeDomain(vkDomain), now);
    }

    private Integer resolveClientId(Long vkUserId) {
        if (vkUserId == null || vkUserId <= 0) {
            throw new IllegalArgumentException("vkUserId is required");
        }
        Integer clientId = dsl.select(VK_LINK_CLIENT_ID)
                .from(VK_LINK)
                .where(VK_LINK_USER_ID.eq(vkUserId))
                .fetchOne(VK_LINK_CLIENT_ID);
        if (clientId == null) {
            throw new IllegalArgumentException("VK profile is not linked");
        }
        return clientId;
    }

    private boolean clientExists(int clientId) {
        return dsl.fetchExists(dsl.selectOne().from(CLIENT).where(CLIENT_ID.eq(clientId)));
    }

    private String findClientName(Integer clientId) {
        return dsl.select(CLIENT_FULL_NAME)
                .from(CLIENT)
                .where(CLIENT_ID.eq(clientId))
                .fetchOne(CLIENT_FULL_NAME);
    }

    private LocalDateTime orderSortDate(OrderDTO order) {
        if (order.getCreated_at() != null) {
            return order.getCreated_at();
        }
        if (order.getDate() != null) {
            return order.getDate().atStartOfDay();
        }
        return LocalDateTime.MIN;
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
        return normalized.matches("\\d{6}") ? normalized : null;
    }

    private String normalizeDomain(String vkDomain) {
        if (vkDomain == null || vkDomain.isBlank()) {
            return null;
        }
        return vkDomain.trim();
    }

    private String fingerprint(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(codePepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("VK code fingerprint is unavailable", e);
        }
    }

    private void recordLinkEvent(
            Integer clientId,
            Long vkUserId,
            String eventType,
            String domain,
            LocalDateTime createdAt
    ) {
        dsl.insertInto(VK_EVENT)
                .columns(VK_EVENT_CLIENT_ID, VK_EVENT_USER_ID, VK_EVENT_TYPE, VK_EVENT_DOMAIN, VK_EVENT_CREATED_AT)
                .values(clientId, vkUserId, eventType, domain, createdAt)
                .execute();
    }

    public record LinkCodeResult(Long codeId, int clientId, String code, LocalDateTime expiresAt) {
    }

    public record LinkConfirmResult(boolean linked, int clientId, Long vkUserId, String clientName) {
    }
}
