package com.shakur.cafehelp.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class ImageStorageExceptionHandler {

    @ExceptionHandler(ImageValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(ImageValidationException exception) {
        HttpStatus status = "FILE_TOO_LARGE".equals(exception.getCode())
                ? HttpStatus.PAYLOAD_TOO_LARGE
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMultipartLimit() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(error("FILE_TOO_LARGE", "Размер изображения не должен превышать 5 МБ"));
    }

    @ExceptionHandler(ImageStorageException.class)
    public ResponseEntity<Map<String, String>> handleStorage(ImageStorageException exception) {
        if ("IMAGE_NOT_FOUND".equals(exception.getCode())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(error(exception.getCode(), exception.getMessage()));
        }
        if ("STORAGE_UNAVAILABLE".equals(exception.getCode())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "5")
                    .body(error(exception.getCode(), exception.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(exception.getCode(), exception.getMessage()));
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("code", code, "message", message);
    }
}
