package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.VkBotLinkConfirmRequestDTO;
import com.shakur.cafehelp.Service.VkClientLinkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/vk-bot")
public class VkBotController {
    private final VkClientLinkService vkClientLinkService;
    private final String apiToken;

    public VkBotController(
            VkClientLinkService vkClientLinkService,
            @Value("${vk.bot.api-token:}") String apiToken
    ) {
        this.vkClientLinkService = vkClientLinkService;
        this.apiToken = apiToken;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(
            @RequestHeader(value = "X-VK-Bot-Token", required = false) String token,
            @RequestParam Long vkUserId
    ) {
        ResponseEntity<?> denied = denyIfInvalidToken(token);
        if (denied != null) return denied;
        return ResponseEntity.ok(vkClientLinkService.getLinkStatus(vkUserId));
    }

    @PostMapping("/link/confirm")
    public ResponseEntity<?> confirmLink(
            @RequestHeader(value = "X-VK-Bot-Token", required = false) String token,
            @RequestBody VkBotLinkConfirmRequestDTO request
    ) {
        ResponseEntity<?> denied = denyIfInvalidToken(token);
        if (denied != null) return denied;

        try {
            return ResponseEntity.ok(vkClientLinkService.confirmLink(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/unlink")
    public ResponseEntity<?> unlink(
            @RequestHeader(value = "X-VK-Bot-Token", required = false) String token,
            @RequestParam Long vkUserId
    ) {
        ResponseEntity<?> denied = denyIfInvalidToken(token);
        if (denied != null) return denied;
        return ResponseEntity.ok(Map.of("unlinked", vkClientLinkService.unlink(vkUserId)));
    }

    @GetMapping("/orders/latest")
    public ResponseEntity<?> latestOrder(
            @RequestHeader(value = "X-VK-Bot-Token", required = false) String token,
            @RequestParam Long vkUserId
    ) {
        ResponseEntity<?> denied = denyIfInvalidToken(token);
        if (denied != null) return denied;

        try {
            Object order = vkClientLinkService.getLatestOrder(vkUserId);
            return order != null ? ResponseEntity.ok(order) : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No orders"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/orders/history")
    public ResponseEntity<?> orderHistory(
            @RequestHeader(value = "X-VK-Bot-Token", required = false) String token,
            @RequestParam Long vkUserId,
            @RequestParam(required = false) Integer limit
    ) {
        ResponseEntity<?> denied = denyIfInvalidToken(token);
        if (denied != null) return denied;

        try {
            return ResponseEntity.ok(vkClientLinkService.getOrderHistory(vkUserId, limit));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/orders/debts")
    public ResponseEntity<?> debtOrders(
            @RequestHeader(value = "X-VK-Bot-Token", required = false) String token,
            @RequestParam Long vkUserId
    ) {
        ResponseEntity<?> denied = denyIfInvalidToken(token);
        if (denied != null) return denied;

        try {
            return ResponseEntity.ok(vkClientLinkService.getDebtOrders(vkUserId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private ResponseEntity<?> denyIfInvalidToken(String token) {
        if (apiToken == null || apiToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "VK bot API is not configured"));
        }
        if (token == null || !MessageDigest.isEqual(
                apiToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        )) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid bot token"));
        }
        return null;
    }
}
