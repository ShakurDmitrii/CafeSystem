package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.Service.MinioStorageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/files")
public class FileUploadController {

    private final MinioStorageService minioStorageService;

    public FileUploadController(MinioStorageService minioStorageService) {
        this.minioStorageService = minioStorageService;
    }

    @PostMapping("/images")
    public UploadResponse uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false) String folder
    ) {
        MinioStorageService.UploadResult uploaded = minioStorageService.uploadImage(file, folder);
        return new UploadResponse(uploaded.key(), uploaded.url());
    }

    @GetMapping("/images/{folder}/{filename}")
    public ResponseEntity<byte[]> getImage(
            @PathVariable String folder,
            @PathVariable String filename
    ) {
        MinioStorageService.StoredImage image = minioStorageService.getImage(folder, filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }

    public record UploadResponse(String key, String url) {}
}
