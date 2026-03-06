package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.Service.MinioStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final MinioStorageService minioStorageService;

    public FileUploadController(MinioStorageService minioStorageService) {
        this.minioStorageService = minioStorageService;
    }

    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false) String folder
    ) {
        try {
            MinioStorageService.UploadResult uploaded = minioStorageService.uploadImage(file, folder);
            return ResponseEntity.ok(Map.of(
                    "key", uploaded.key(),
                    "url", uploaded.url()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Ошибка загрузки файла"));
        }
    }
}
