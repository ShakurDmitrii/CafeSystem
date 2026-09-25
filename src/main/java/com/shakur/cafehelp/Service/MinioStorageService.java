package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.config.MinioProperties;
import com.shakur.cafehelp.exception.ImageStorageException;
import com.shakur.cafehelp.exception.ImageValidationException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ListObjectsArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Service
public class MinioStorageService {

    private static final Set<String> ALLOWED_FOLDERS = Set.of("products", "dishes", "dish-sets", "misc");
    private static final Pattern FILE_NAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(?:jpg|png)"
    );
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "png", "image/png"
    );

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private volatile boolean bucketChecked;

    public MinioStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public UploadResult uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new ImageValidationException("EMPTY_FILE", "Файл изображения пустой");
        }
        if (file.getSize() > properties.getMaxImageSizeBytes()) {
            throw new ImageValidationException("FILE_TOO_LARGE", "Размер изображения не должен превышать 5 МБ");
        }

        String safeFolder = validateFolder(folder);
        byte[] bytes = readUpload(file);
        if (bytes.length > properties.getMaxImageSizeBytes()) {
            throw new ImageValidationException("FILE_TOO_LARGE", "Размер изображения не должен превышать 5 МБ");
        }
        DetectedImage detected = detectAndValidate(bytes);
        validateDeclaredContentType(file.getContentType(), detected.contentType());
        String baseUrl = trimTrailingSlash(properties.getPublicBaseUrl());

        String objectKey = safeFolder + "/" + UUID.randomUUID() + "." + detected.extension();
        try {
            ensureBucketExists();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType(detected.contentType())
                            .build()
            );
        } catch (Exception e) {
            throw unavailable(e);
        }

        return new UploadResult(objectKey, baseUrl + "/api/v1/files/images/" + objectKey);
    }

    public StoredImage getImage(String folder, String filename) {
        String objectKey = validateFolder(folder) + "/" + validateFilename(filename);
        String extension = filename.substring(filename.lastIndexOf('.') + 1);
        try {
            ensureBucketExists();
            try (GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).build()
            )) {
                byte[] bytes = response.readNBytes(Math.toIntExact(properties.getMaxImageSizeBytes() + 1));
                if (bytes.length > properties.getMaxImageSizeBytes()) {
                    throw new ImageStorageException(
                            "CORRUPT_STORED_IMAGE",
                            "Сохранённое изображение превышает допустимый размер"
                    );
                }
                return new StoredImage(bytes, CONTENT_TYPES.get(extension));
            }
        } catch (ImageStorageException e) {
            throw e;
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() == null ? "" : e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                throw new ImageStorageException("IMAGE_NOT_FOUND", "Изображение не найдено", e);
            }
            throw unavailable(e);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    public int removeExpiredOrphans(Predicate<String> isReferenced) {
        ZonedDateTime cutoff = ZonedDateTime.now(ZoneOffset.UTC)
                .minusHours(properties.getCleanupGraceHours());
        int removed = 0;
        try {
            ensureBucketExists();
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(properties.getBucket()).recursive(true).build()
            );
            for (Result<Item> objectResult : objects) {
                if (removed >= properties.getCleanupBatchSize()) break;
                Item item = objectResult.get();
                String objectKey = item.objectName();
                if (item.isDir()
                        || item.lastModified() == null
                        || !item.lastModified().isBefore(cutoff)
                        || !isManagedObjectKey(objectKey)
                        || isReferenced.test(objectKey)) {
                    continue;
                }
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(properties.getBucket())
                                .object(objectKey)
                                .build()
                );
                removed++;
            }
            return removed;
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    private byte[] readUpload(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ImageValidationException("UNREADABLE_FILE", "Не удалось прочитать изображение");
        }
    }

    private DetectedImage detectAndValidate(byte[] bytes) {
        String extension;
        String contentType;
        if (hasPngSignature(bytes)) {
            extension = "png";
            contentType = "image/png";
        } else if (hasJpegSignature(bytes)) {
            extension = "jpg";
            contentType = "image/jpeg";
        } else {
            throw new ImageValidationException(
                    "UNSUPPORTED_IMAGE",
                    "Разрешены только корректные изображения JPEG и PNG"
            );
        }

        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (imageInput == null) {
                throw new ImageValidationException("CORRUPT_IMAGE", "Не удалось распознать изображение");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new ImageValidationException("CORRUPT_IMAGE", "Не удалось распознать изображение");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width <= 0 || height <= 0
                    || width > properties.getMaxImageWidth()
                    || height > properties.getMaxImageHeight()
                    || pixels > properties.getMaxImagePixels()) {
                    throw new ImageValidationException("IMAGE_DIMENSIONS_TOO_LARGE", "Размеры изображения слишком велики");
                }
                // Полное чтение после проверки размеров выявляет повреждённые данные,
                // не позволяя сжатому файлу заранее выделить неограниченную память.
                reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ImageValidationException("CORRUPT_IMAGE", "Файл изображения повреждён");
        }
        return new DetectedImage(extension, contentType);
    }

    private void validateDeclaredContentType(String declared, String detected) {
        if (declared == null || declared.isBlank()) {
            throw new ImageValidationException("MISSING_CONTENT_TYPE", "Не указан MIME-тип изображения");
        }
        String normalized = declared.toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalized)) normalized = "image/jpeg";
        if (!normalized.equals(detected)) {
            throw new ImageValidationException(
                    "CONTENT_TYPE_MISMATCH",
                    "MIME-тип не соответствует содержимому изображения"
            );
        }
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

    private static String validateFolder(String folder) {
        String normalized = folder == null || folder.isBlank()
                ? "misc"
                : folder.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_FOLDERS.contains(normalized)) {
            throw new ImageValidationException("INVALID_FOLDER", "Недопустимая папка для изображения");
        }
        return normalized;
    }

    private static String validateFilename(String filename) {
        if (filename == null || !FILE_NAME.matcher(filename).matches()) {
            throw new ImageValidationException("INVALID_IMAGE_KEY", "Некорректный ключ изображения");
        }
        return filename;
    }

    private static boolean isManagedObjectKey(String objectKey) {
        if (objectKey == null) return false;
        int separator = objectKey.indexOf('/');
        if (separator <= 0 || separator != objectKey.lastIndexOf('/')) return false;
        return ALLOWED_FOLDERS.contains(objectKey.substring(0, separator))
                && FILE_NAME.matcher(objectKey.substring(separator + 1)).matches();
    }

    private static boolean hasPngSignature(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) return false;
        }
        return true;
    }

    private static boolean hasJpegSignature(byte[] bytes) {
        return bytes.length >= 4
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[bytes.length - 2] & 0xff) == 0xff
                && (bytes[bytes.length - 1] & 0xff) == 0xd9;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new ImageStorageException(
                    "INVALID_STORAGE_CONFIGURATION",
                    "Не настроен публичный адрес backend"
            );
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static ImageStorageException unavailable(Exception cause) {
        return new ImageStorageException(
                "STORAGE_UNAVAILABLE",
                "Хранилище изображений временно недоступно",
                cause
        );
    }

    private record DetectedImage(String extension, String contentType) {}

    public record UploadResult(String key, String url) {}

    public record StoredImage(byte[] bytes, String contentType) {}
}
