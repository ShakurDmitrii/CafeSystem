package com.shakur.cafehelp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tax.dispatch")
public class TaxDispatchProperties {

    private String mode = "SAFE";
    private String sourceSystem = "cafehelp";
    private final Partner partner = new Partner();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public Partner getPartner() {
        return partner;
    }

    public String normalizedMode() {
        return mode == null ? "SAFE" : mode.trim().toUpperCase();
    }

    public boolean isSafeMode() {
        return "SAFE".equals(normalizedMode());
    }

    public boolean isPartnerMode() {
        return "PARTNER".equals(normalizedMode());
    }

    public boolean isPartnerConfigured() {
        return !isBlank(partner.baseUrl) && !isBlank(partner.apiKey);
    }

    public static class Partner {
        private String baseUrl;
        private String sendPath = "/api/v1/receipts";
        private String apiKey;
        private String apiKeyHeader = "X-API-Key";
        private int timeoutMs = 15000;
        private String providerName = "Официальный партнер ФНС";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getSendPath() {
            return sendPath;
        }

        public void setSendPath(String sendPath) {
            this.sendPath = sendPath;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiKeyHeader() {
            return apiKeyHeader;
        }

        public void setApiKeyHeader(String apiKeyHeader) {
            this.apiKeyHeader = apiKeyHeader;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getProviderName() {
            return providerName;
        }

        public void setProviderName(String providerName) {
            this.providerName = providerName;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
