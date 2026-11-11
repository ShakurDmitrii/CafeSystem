package com.shakur.cafehelp.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Единый формат ошибок API: {@code {"code": "...", "message": "понятный текст", "details": [...]}}.
 * <p>
 * Раньше часть отказов ({@link ResponseStatusException}, RuntimeException из сервисов) доходила до
 * интерфейса как «Bad Request»/«Internal Server Error» без причины, потому что Spring по умолчанию
 * не отдаёт текст ошибки. Этот обработчик возвращает причину, написанную для человека, а
 * технические подробности пишет только в лог вместе с кодом обращения.
 * <p>
 * Обработчики внутри контроллеров и более специфичные советы (изображения, ML) срабатывают раньше.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);
    private static final Pattern CYRILLIC = Pattern.compile("[А-Яа-яЁё]");
    private static final Pattern CONSTRAINT = Pattern.compile("constraint \"([^\"]+)\"");
    private static final Pattern NOT_NULL_COLUMN = Pattern.compile("column \"([^\"]+)\"");

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> responseStatus(ResponseStatusException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String reason = exception.getReason();
        String message = reason != null && !reason.isBlank() ? reason : describeStatus(status.value());
        return body(status, codeFor(status.value()), message, null);
    }

    @ExceptionHandler({
            InvalidOrderRequestException.class,
            InvalidShiftRequestException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Map<String, Object>> badRequest(RuntimeException exception) {
        return body(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", humanOr(exception, "Некорректные данные запроса"), null);
    }

    @ExceptionHandler({OrderNotFoundException.class, ShiftNotFoundException.class})
    public ResponseEntity<Map<String, Object>> notFound(RuntimeException exception) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", humanOr(exception, "Запись не найдена"), null);
    }

    @ExceptionHandler({
            OrderStateConflictException.class,
            ShiftStateConflictException.class,
            IllegalStateException.class
    })
    public ResponseEntity<Map<String, Object>> conflict(RuntimeException exception, HttpServletRequest request) {
        if (!isHumanReadable(exception.getMessage())) {
            return internal(exception, request);
        }
        return body(HttpStatus.CONFLICT, "STATE_CONFLICT", exception.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> "«" + error.getField() + "»: " + error.getDefaultMessage())
                .toList();
        String message = details.isEmpty()
                ? "Проверьте заполнение формы"
                : "Проверьте поля: " + String.join("; ", details);
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String message = "Не удалось прочитать данные запроса: проверьте формат полей";
        if (cause instanceof InvalidFormatException invalid) {
            message = "Поле «" + fieldPath(invalid) + "» имеет неверный формат: «" + invalid.getValue() + "»";
        } else if (cause instanceof MismatchedInputException mismatched && !mismatched.getPath().isEmpty()) {
            message = "Поле «" + fieldPath(mismatched) + "» имеет неверный тип или формат";
        } else if (exception.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            message = "Поле «" + fieldPath(mapping) + "» имеет неверный формат";
        }
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", message, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException exception) {
        String expected = exception.getRequiredType() != null ? exception.getRequiredType().getSimpleName() : "";
        String hint = switch (expected) {
            case "int", "Integer", "long", "Long" -> "должен быть целым числом";
            case "double", "Double", "BigDecimal" -> "должен быть числом";
            case "boolean", "Boolean" -> "должен быть true или false";
            case "LocalDate" -> "должен быть датой в формате ГГГГ-ММ-ДД";
            default -> "имеет неверный формат";
        };
        return body(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Параметр «" + exception.getName() + "» " + hint + " (получено «" + exception.getValue() + "»)", null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> missingParameter(MissingServletRequestParameterException exception) {
        return body(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Не передан обязательный параметр «" + exception.getParameterName() + "»", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> methodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Операция " + exception.getMethod() + " для этого адреса не поддерживается. Возможно, версия программы устарела", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> noResource(NoResourceFoundException exception) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "Адрес /" + exception.getResourcePath() + " не найден на сервере. Возможно, версия программы устарела", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> accessDenied(AccessDeniedException exception) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "Недостаточно прав для этого действия", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException exception) {
        String raw = String.valueOf(exception.getMostSpecificCause().getMessage());
        log.warn("Нарушение целостности данных: {}", raw);
        return body(HttpStatus.CONFLICT, "DATA_INTEGRITY", describeIntegrity(raw), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof RuntimeException && isHumanReadable(exception.getMessage())) {
            // Сервисы проекта бросают RuntimeException с русским текстом для пользователя
            // («Уже есть открытая смена у: …», «Заказ с id 5 не найден»): это отказ, а не сбой.
            log.info("Отказ в операции {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
            return body(HttpStatus.BAD_REQUEST, "OPERATION_REJECTED", exception.getMessage(), null);
        }
        return internal(exception, request);
    }

    private ResponseEntity<Map<String, Object>> internal(Exception exception, HttpServletRequest request) {
        String incident = UUID.randomUUID().toString().substring(0, 8);
        log.error("Внутренняя ошибка [{}] при {} {}", incident, request.getMethod(), request.getRequestURI(), exception);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Внутренняя ошибка сервера. Код для поиска в логе: " + incident, null);
    }

    static String describeIntegrity(String raw) {
        String lower = raw.toLowerCase();
        String constraint = firstGroup(CONSTRAINT, raw);
        String suffix = constraint != null ? " (ограничение " + constraint + ")" : "";
        if (lower.contains("foreign key")) {
            if (lower.contains("still referenced") || lower.contains("update or delete")) {
                return "Нельзя удалить или изменить запись: она используется в других данных" + suffix;
            }
            return "Связанная запись не найдена: проверьте выбранные значения" + suffix;
        }
        if (lower.contains("duplicate key") || lower.contains("unique")) {
            return "Такая запись уже существует" + suffix;
        }
        if (lower.contains("null value")) {
            String column = firstGroup(NOT_NULL_COLUMN, raw);
            return "Не заполнено обязательное поле" + (column != null ? " «" + column + "»" : "");
        }
        if (lower.contains("check constraint")) {
            return "Значение не прошло проверку базы данных" + suffix;
        }
        return "Операция нарушает целостность данных" + suffix;
    }

    static boolean isHumanReadable(String message) {
        return message != null && !message.isBlank() && CYRILLIC.matcher(message).find();
    }

    private static String humanOr(RuntimeException exception, String fallback) {
        String message = exception.getMessage();
        return message != null && !message.isBlank() ? message : fallback;
    }

    private static String fieldPath(JsonMappingException exception) {
        return exception.getPath().stream()
                .map(reference -> reference.getFieldName() != null
                        ? reference.getFieldName()
                        : "[" + reference.getIndex() + "]")
                .collect(Collectors.joining("."))
                .replace(".[", "[");
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    static String describeStatus(int status) {
        return switch (status) {
            case 400 -> "Некорректный запрос";
            case 401 -> "Требуется вход в систему";
            case 403 -> "Недостаточно прав для этого действия";
            case 404 -> "Запись не найдена";
            case 409 -> "Операция конфликтует с текущим состоянием данных";
            case 413 -> "Слишком большой файл или запрос";
            case 503 -> "Сервис временно недоступен";
            default -> status >= 500 ? "Внутренняя ошибка сервера" : "Запрос отклонён (" + status + ")";
        };
    }

    private static String codeFor(int status) {
        return switch (status) {
            case 400 -> "INVALID_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "STATE_CONFLICT";
            default -> status >= 500 ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
        };
    }

    private static ResponseEntity<Map<String, Object>> body(
            HttpStatusCode status,
            String code,
            String message,
            List<String> details
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("status", status.value());
        if (details != null && !details.isEmpty()) {
            body.put("details", details);
        }
        return ResponseEntity.status(status).body(body);
    }
}
