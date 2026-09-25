package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.Service.MinioStorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Временный совместимый маршрут для клиентов, которые ещё не перешли на /api/v1/files/images.
 */
@RestController
@RequestMapping("/api/files")
public class LegacyFileUploadController {

    private final MinioStorageService storageService;

    public LegacyFileUploadController(MinioStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/upload-image")
    public FileUploadController.UploadResponse uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false) String folder
    ) {
        MinioStorageService.UploadResult uploaded = storageService.uploadImage(file, folder);
        return new FileUploadController.UploadResponse(uploaded.key(), uploaded.url());
    }
}
