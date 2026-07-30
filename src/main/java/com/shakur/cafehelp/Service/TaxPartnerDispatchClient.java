package com.shakur.cafehelp.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shakur.cafehelp.config.TaxDispatchProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class TaxPartnerDispatchClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TaxPartnerDispatchClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public PartnerResponse sendReceipt(TaxDispatchProperties properties, Map<String, Object> payload) throws Exception {
        TaxDispatchProperties.Partner partner = properties.getPartner();
        URI uri = buildUri(partner.getBaseUrl(), partner.getSendPath());
        int timeoutMs = Math.max(1000, partner.getTimeoutMs());

        String requestBody = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header(
                        normalizeHeaderName(partner.getApiKeyHeader()),
                        partner.getApiKey() != null ? partner.getApiKey() : ""
                )
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        JsonNode body = parseJson(response.body());
        String receiptId = firstNonBlank(
                textAt(body, "receiptId"),
                textAt(body, "providerReceiptId"),
                textAt(body, "externalReceiptId"),
                textAt(body, "id"),
                textAt(body, "data.receiptId"),
                textAt(body, "data.id")
        );
        String receiptUrl = firstNonBlank(
                textAt(body, "receiptUrl"),
                textAt(body, "printUrl"),
                textAt(body, "data.receiptUrl"),
                textAt(body, "data.printUrl")
        );

        return new PartnerResponse(
                statusCode >= 200 && statusCode < 300,
                statusCode,
                response.body() != null ? response.body() : "{}",
                receiptId,
                receiptUrl
        );
    }

    private URI buildUri(String baseUrl, String path) {
        String normalizedBase = (baseUrl == null ? "" : baseUrl.trim()).replaceAll("/+$", "");
        String normalizedPath = path == null || path.trim().isEmpty() ? "/api/v1/receipts" : path.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return URI.create(normalizedBase + normalizedPath);
    }

    private String normalizeHeaderName(String headerName) {
        String normalized = headerName == null ? "" : headerName.trim();
        return normalized.isEmpty() ? "X-API-Key" : normalized;
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String textAt(JsonNode node, String dottedPath) {
        if (node == null || dottedPath == null) {
            return null;
        }
        JsonNode current = node;
        for (String part : dottedPath.split("\\.")) {
            current = current.path(part);
        }
        return current.isMissingNode() || current.isNull() ? null : current.asText();
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    public record PartnerResponse(
            boolean success,
            int httpStatus,
            String responseBody,
            String receiptId,
            String receiptUrl
    ) {
    }
}
