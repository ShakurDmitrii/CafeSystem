package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.config.MinioProperties;
import com.shakur.cafehelp.exception.ImageStorageException;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.ListObjectsArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioStorageServiceTest {

    private MinioClient minioClient;
    private MinioStorageService service;

    @BeforeEach
    void setUp() throws Exception {
        minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("http://minio:9000");
        properties.setAccessKey("test-access");
        properties.setSecretKey("test-secret");
        properties.setBucket("test-images");
        properties.setPublicBaseUrl("http://localhost:8080");
        service = new MinioStorageService(minioClient, properties);
    }

    @Test
    void validPngUsesDetectedCanonicalExtensionInsteadOfOriginalFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../avatar.svg",
                "image/png",
                pngBytes()
        );

        MinioStorageService.UploadResult result = service.uploadImage(file, "products");

        assertThat(result.key()).matches("products/[0-9a-f-]{36}\\.png");
        assertThat(result.url()).isEqualTo("http://localhost:8080/api/v1/files/images/" + result.key());
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void rejectsTextDisguisedAsPng() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.png",
                "image/png",
                "not an image".getBytes()
        );

        assertThatThrownBy(() -> service.uploadImage(file, "products"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("изображ");
    }

    @Test
    void rejectsFilesLargerThanFiveMegabytesBeforeCallingStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.png",
                "image/png",
                new byte[5 * 1024 * 1024 + 1]
        );

        assertThatThrownBy(() -> service.uploadImage(file, "products"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5");
    }

    @Test
    void rejectsUnknownOrNestedFolder() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                new byte[]{1}
        );

        assertThatThrownBy(() -> service.uploadImage(file, "../../private"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("папк");
    }

    @Test
    void reportsStorageUnavailabilityWithoutLeakingMinioDetails() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new IOException("connection refused at internal-host:9000"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                pngBytes()
        );

        assertThatThrownBy(() -> service.uploadImage(file, "products"))
                .isInstanceOfSatisfying(ImageStorageException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("STORAGE_UNAVAILABLE");
                    assertThat(exception.getMessage()).doesNotContain("internal-host");
                });
    }

    @Test
    void cleanupDeletesOnlyExpiredManagedObjectWithoutDatabaseReference() throws Exception {
        @SuppressWarnings("unchecked")
        Result<Item> itemResult = mock(Result.class);
        Item item = mock(Item.class);
        when(itemResult.get()).thenReturn(item);
        when(item.objectName()).thenReturn("products/7aa0a8ef-bf72-4f7d-9ca3-c16c19152c89.png");
        when(item.lastModified()).thenReturn(ZonedDateTime.now(ZoneOffset.UTC).minusDays(2));
        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(itemResult));

        int removed = service.removeExpiredOrphans(key -> false);

        assertThat(removed).isEqualTo(1);
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void cleanupKeepsReferencedObject() throws Exception {
        @SuppressWarnings("unchecked")
        Result<Item> itemResult = mock(Result.class);
        Item item = mock(Item.class);
        when(itemResult.get()).thenReturn(item);
        when(item.objectName()).thenReturn("dishes/7aa0a8ef-bf72-4f7d-9ca3-c16c19152c89.jpg");
        when(item.lastModified()).thenReturn(ZonedDateTime.now(ZoneOffset.UTC).minusDays(2));
        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(itemResult));

        int removed = service.removeExpiredOrphans(key -> true);

        assertThat(removed).isZero();
        org.mockito.Mockito.verify(minioClient, org.mockito.Mockito.never())
                .removeObject(any(RemoveObjectArgs.class));
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.WHITE.getRGB());
        image.setRGB(1, 0, Color.BLACK.getRGB());
        image.setRGB(0, 1, Color.BLACK.getRGB());
        image.setRGB(1, 1, Color.WHITE.getRGB());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
