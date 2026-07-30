package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.OrderDTO;
import com.shakur.cafehelp.DTO.VkBotLinkConfirmRequestDTO;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
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

    private final DSLContext dsl;
    private final BCryptPasswordEncoder passwordEncoder;
    private final OrderService orderService;
    private final SecureRandom random = new SecureRandom();

    public VkClientLinkService(DSLContext dsl, BCryptPasswordEncoder passwordEncoder, OrderService orderService) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
        this.orderService = orderService;
    }

    @Transactional
    public LinkCodeResult createLinkCode(int clientId) {
        if (clientId <= 0 || !clientExists(clientId)) {
            throw new IllegalArgumentException("Client not found");
        }

        LocalDateTime now = LocalDateTime.now();
        dsl.update(VK_CODE)
                .set(VK_CODE_USED_AT, now)
                .where(VK_CODE_CLIENT_ID.eq(clientId))
                .and(VK_CODE_USED_AT.isNull())
                .execute();

        String code = generateCode();
        LocalDateTime expiresAt = now.plusMinutes(15);
        Long codeId = dsl.insertInto(VK_CODE)
                .columns(VK_CODE_CLIENT_ID, VK_CODE_HASH, VK_CODE_EXPIRES_AT, VK_CODE_CREATED_AT)
                .values(clientId, passwordEncoder.encode(code), expiresAt, now)
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

        LocalDateTime now = LocalDateTime.now();
        var candidates = dsl.select(VK_CODE_ID, VK_CODE_CLIENT_ID, VK_CODE_HASH, VK_CODE_EXPIRES_AT)
                .from(VK_CODE)
                .where(VK_CODE_USED_AT.isNull())
                .and(VK_CODE_EXPIRES_AT.gt(now))
                .orderBy(VK_CODE_CREATED_AT.desc())
                .limit(50)
                .fetch();

        for (var candidate : candidates) {
            String hash = candidate.get(VK_CODE_HASH);
            if (hash != null && passwordEncoder.matches(code, hash)) {
                Integer clientId = candidate.get(VK_CODE_CLIENT_ID);
                upsertLink(clientId, request.getVkUserId(), request.getVkDomain(), now);
                dsl.update(VK_CODE)
                        .set(VK_CODE_USED_AT, now)
                        .where(VK_CODE_ID.eq(candidate.get(VK_CODE_ID)))
                        .execute();
                return new LinkConfirmResult(true, clientId, request.getVkUserId(), findClientName(clientId));
            }
        }

        throw new IllegalArgumentException("Invalid or expired code");
    }

    @Transactional
    public boolean unlink(Long vkUserId) {
        if (vkUserId == null || vkUserId <= 0) {
            return false;
        }
        return dsl.deleteFrom(VK_LINK)
                .where(VK_LINK_USER_ID.eq(vkUserId))
                .execute() > 0;
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
        dsl.deleteFrom(VK_LINK)
                .where(VK_LINK_CLIENT_ID.eq(clientId).or(VK_LINK_USER_ID.eq(vkUserId)))
                .execute();

        dsl.insertInto(VK_LINK)
                .columns(VK_LINK_CLIENT_ID, VK_LINK_USER_ID, VK_LINK_DOMAIN, VK_LINK_VERIFIED_AT, VK_LINK_CREATED_AT)
                .values(clientId, vkUserId, normalizeDomain(vkDomain), now, now)
                .execute();
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
        String normalized = code.replaceAll("\\D", "");
        return normalized.length() == 6 ? normalized : null;
    }

    private String normalizeDomain(String vkDomain) {
        if (vkDomain == null || vkDomain.isBlank()) {
            return null;
        }
        return vkDomain.trim();
    }

    public record LinkCodeResult(Long codeId, int clientId, String code, LocalDateTime expiresAt) {
    }

    public record LinkConfirmResult(boolean linked, int clientId, Long vkUserId, String clientName) {
    }
}
