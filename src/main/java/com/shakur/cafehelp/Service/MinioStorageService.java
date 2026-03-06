package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

@Service
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private volatile boolean bucketChecked;

    public MinioStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public UploadResult uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Разрешены только изображения");
        }

        String safeFolder = sanitizeFolder(folder);
        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        String objectKey = safeFolder + "/" + UUID.randomUUID() + extension;

        try {
            ensureBucketExists();
            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(properties.getBucket())
                                .object(objectKey)
                                .stream(in, file.getSize(), -1)
                                .contentType(contentType)
                                .build()
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Не удалось загрузить файл в MinIO", e);
        }

        String baseUrl = (properties.getPublicBaseUrl() == null || properties.getPublicBaseUrl().isBlank())
                ? properties.getEndpoint()
                : properties.getPublicBaseUrl();
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String url = baseUrl + "/" + properties.getBucket() + "/" + objectKey;
        return new UploadResult(objectKey, url);
    }

    private void ensureBucketExists() throws Exception {
        if (bucketChecked) return;
        synchronized (this) {
            if (bucketChecked) return;
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucket()).build()
            );
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            }
            bucketChecked = true;
        }
    }

    private static String sanitizeFolder(String folder) {
        if (folder == null || folder.isBlank()) return "misc";
        String sanitized = folder.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/_-]", "");
        if (sanitized.isBlank()) return "misc";
        return sanitized.startsWith("/") ? sanitized.substring(1) : sanitized;
    }

    private static String resolveExtension(String originalName, String contentType) {
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0 && dot < originalName.length() - 1) {
                return originalName.substring(dot);
            }
        }
        if (contentType.equalsIgnoreCase("image/jpeg")) return ".jpg";
        if (contentType.equalsIgnoreCase("image/png")) return ".png";
        if (contentType.equalsIgnoreCase("image/webp")) return ".webp";
        return ".bin";
    }

    public record UploadResult(String key, String url) {}
}
