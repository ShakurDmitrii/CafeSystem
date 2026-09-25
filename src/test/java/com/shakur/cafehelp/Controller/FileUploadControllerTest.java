package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.Service.MinioStorageService;
import com.shakur.cafehelp.exception.ImageStorageException;
import com.shakur.cafehelp.exception.ImageStorageExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileUploadControllerTest {

    private MinioStorageService storageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        storageService = mock(MinioStorageService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FileUploadController(storageService))
                .setControllerAdvice(new ImageStorageExceptionHandler())
                .build();
    }

    @Test
    void uploadReturnsStableKeyAndBackendUrl() throws Exception {
        when(storageService.uploadImage(any(), eq("products"))).thenReturn(
                new MinioStorageService.UploadResult(
                        "products/7aa0a8ef-bf72-4f7d-9ca3-c16c19152c89.png",
                        "http://localhost:8080/api/v1/files/images/products/7aa0a8ef-bf72-4f7d-9ca3-c16c19152c89.png"
                )
        );

        mockMvc.perform(multipart("/api/v1/files/images")
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}))
                        .param("folder", "products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("products/7aa0a8ef-bf72-4f7d-9ca3-c16c19152c89.png"))
                .andExpect(jsonPath("$.url").value("http://localhost:8080/api/v1/files/images/products/7aa0a8ef-bf72-4f7d-9ca3-c16c19152c89.png"));
    }

    @Test
    void storageFailureReturnsRetryableServiceUnavailable() throws Exception {
        when(storageService.uploadImage(any(), eq("products"))).thenThrow(
                new ImageStorageException("STORAGE_UNAVAILABLE", "Хранилище изображений временно недоступно")
        );

        mockMvc.perform(multipart("/api/v1/files/images")
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}))
                        .param("folder", "products"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));
    }

    @Test
    void imageResponseHasFixedTypeCachingAndNosniff() throws Exception {
        when(storageService.getImage("products", "7aa0a8ef-bf72-4f7d-9ca3-c16c19152c89.png"))
                .thenReturn(new MinioStorageService.StoredImage(new byte[]{1, 2, 3}, "image/png"));

        mockMvc.perform(get("/api/v1/files/images/products/7aa0a8ef-bf72-4f7d-9ca3-c16c19152c89.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "max-age=2592000, public, immutable"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }
}
