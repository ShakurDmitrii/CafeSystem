package com.shakur.cafehelp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.minio")
public class MinioProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String publicBaseUrl;
    private long maxImageSizeBytes = 5L * 1024 * 1024;
    private int maxImageWidth = 8_000;
    private int maxImageHeight = 8_000;
    private long maxImagePixels = 40_000_000;
    private boolean cleanupEnabled = true;
    private long cleanupGraceHours = 24;
    private int cleanupBatchSize = 100;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public long getMaxImageSizeBytes() { return maxImageSizeBytes; }
    public void setMaxImageSizeBytes(long value) { this.maxImageSizeBytes = value; }
    public int getMaxImageWidth() { return maxImageWidth; }
    public void setMaxImageWidth(int value) { this.maxImageWidth = value; }
    public int getMaxImageHeight() { return maxImageHeight; }
    public void setMaxImageHeight(int value) { this.maxImageHeight = value; }
    public long getMaxImagePixels() { return maxImagePixels; }
    public void setMaxImagePixels(long value) { this.maxImagePixels = value; }
    public boolean isCleanupEnabled() { return cleanupEnabled; }
    public void setCleanupEnabled(boolean value) { this.cleanupEnabled = value; }
    public long getCleanupGraceHours() { return cleanupGraceHours; }
    public void setCleanupGraceHours(long value) { this.cleanupGraceHours = value; }
    public int getCleanupBatchSize() { return cleanupBatchSize; }
    public void setCleanupBatchSize(int value) { this.cleanupBatchSize = value; }
}
