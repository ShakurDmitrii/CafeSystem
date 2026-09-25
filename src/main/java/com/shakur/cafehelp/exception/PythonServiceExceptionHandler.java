package com.shakur.cafehelp.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PythonServiceExceptionHandler {

    @ExceptionHandler(PythonServiceException.class)
    public ResponseEntity<Map<String, String>> handlePythonServiceException(
            PythonServiceException exception
    ) {
        String code = "PYTHON_" + exception.getCode().name();
        String message = switch (exception.getCode()) {
            case TIMEOUT -> "Сервис аналитики не ответил вовремя";
            case INVALID_RESPONSE -> "Сервис аналитики вернул некорректный ответ";
            case UPSTREAM_ERROR -> "Сервис аналитики отклонил внутренний запрос";
            case UNAVAILABLE -> "Сервис аналитики временно недоступен";
            case INPUT_INVALID -> exception.getMessage();
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getHttpStatus());
        if (exception.getHttpStatus() == HttpStatus.SERVICE_UNAVAILABLE
                || exception.getHttpStatus() == HttpStatus.GATEWAY_TIMEOUT) {
            response.header(HttpHeaders.RETRY_AFTER, "5");
        }
        return response.body(Map.of("code", code, "message", message));
    }
}
