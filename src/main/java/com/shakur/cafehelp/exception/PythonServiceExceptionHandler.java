package com.shakur.cafehelp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PythonServiceExceptionHandler {

    @ExceptionHandler(PythonServiceException.class)
    public ResponseEntity<Map<String, String>> handlePythonServiceException() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", "Сервис аналитики временно недоступен"));
    }
}
