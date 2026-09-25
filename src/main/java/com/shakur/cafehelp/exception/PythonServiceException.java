package com.shakur.cafehelp.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;

import java.net.SocketTimeoutException;

public class PythonServiceException extends RuntimeException {

    public enum Code {
        UNAVAILABLE,
        TIMEOUT,
        UPSTREAM_ERROR,
        INPUT_INVALID,
        INVALID_RESPONSE
    }

    private final Code code;

    public PythonServiceException(String message, Throwable cause) {
        this(Code.UNAVAILABLE, message, cause);
    }

    public PythonServiceException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return switch (code) {
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case UPSTREAM_ERROR, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case INPUT_INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    public static PythonServiceException timeout(Throwable cause) {
        return new PythonServiceException(Code.TIMEOUT, "Python service timed out", cause);
    }

    public static PythonServiceException invalidResponse(Throwable cause) {
        return new PythonServiceException(Code.INVALID_RESPONSE, "Python service returned an invalid response", cause);
    }

    public static PythonServiceException translate(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return timeout(cause);
            }
            if (current instanceof JsonProcessingException) {
                return invalidResponse(cause);
            }
            current = current.getCause();
        }
        if (cause instanceof HttpStatusCodeException response) {
            if (response.getStatusCode().value() == 422 && response.getResponseBodyAsByteArray().length <= 16_384) {
                try {
                    var detail = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(response.getResponseBodyAsByteArray()).path("detail");
                    if ("ML_INPUT_INVALID".equals(detail.path("code").asText())
                            && detail.path("message").isTextual()) {
                        String message = detail.path("message").asText();
                        if (!message.isBlank() && message.length() <= 4000) {
                            return new PythonServiceException(Code.INPUT_INVALID, message, cause);
                        }
                    }
                } catch (java.io.IOException ignored) {
                    // Framework errors and unknown response shapes remain masked upstream errors.
                }
            }
            return new PythonServiceException(Code.UPSTREAM_ERROR, "Python service rejected the request", cause);
        }
        return new PythonServiceException(Code.UNAVAILABLE, "Python service is unavailable", cause);
    }
}
