package com.shakur.cafehelp.Controller.PyController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shakur.cafehelp.exception.PythonServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

/**
 * Клиент распознавания речи PyModule ({@code POST /api/speech/transcribe}).
 * Использует общий {@code restTemplate} с внутренним токеном и версией контракта.
 */
@Service
@Slf4j
public class PythonSpeechClient {

    public record Transcription(String text, Double confidence, Double durationSeconds) {
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String pythonApiUrl;

    public PythonSpeechClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${python.api.url:http://localhost:8000}") String pythonApiUrl
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.pythonApiUrl = pythonApiUrl.replaceAll("/+$", "");
    }

    public Transcription transcribe(byte[] audio, String filename, String contentType, List<String> hints) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("audio", new NamedByteArrayResource(audio, filename, contentType));
        try {
            form.add("hints", objectMapper.writeValueAsString(hints));
        } catch (IOException e) {
            throw PythonServiceException.invalidResponse(e);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        String url = pythonApiUrl + "/api/speech/transcribe";

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(form, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw PythonServiceException.invalidResponse(new IllegalStateException("Missing speech response body"));
            }
            JsonNode body = objectMapper.readTree(response.getBody());
            JsonNode text = body.path("text");
            if (!text.isTextual()) {
                throw PythonServiceException.invalidResponse(new IllegalStateException("Speech response has no text"));
            }
            return new Transcription(
                    text.asText(),
                    body.path("confidence").isNumber() ? body.path("confidence").asDouble() : null,
                    body.path("durationSeconds").isNumber() ? body.path("durationSeconds").asDouble() : null
            );
        } catch (PythonServiceException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            log.warn("Speech request rejected by PyModule with status {}", e.getStatusCode());
            throw translateSpeechError(e);
        } catch (Exception e) {
            log.error("Speech request failed: {}", e.getClass().getSimpleName());
            throw PythonServiceException.translate(e);
        }
    }

    private PythonServiceException translateSpeechError(HttpStatusCodeException e) {
        int status = e.getStatusCode().value();
        if ((status == 422 || status == 413 || status == 503) && e.getResponseBodyAsByteArray().length <= 16_384) {
            try {
                JsonNode detail = objectMapper.readTree(e.getResponseBodyAsByteArray()).path("detail");
                String code = detail.path("code").asText("");
                String message = detail.path("message").asText("");
                if ("SPEECH_INPUT_INVALID".equals(code) && !message.isBlank()) {
                    return new PythonServiceException(PythonServiceException.Code.INPUT_INVALID, message, e);
                }
                if ("SPEECH_UNAVAILABLE".equals(code)) {
                    return new PythonServiceException(PythonServiceException.Code.UNAVAILABLE, message, e);
                }
            } catch (IOException ignored) {
                // Неизвестный формат ошибки остаётся «внешней» ошибкой.
            }
        }
        return PythonServiceException.translate(e);
    }

    /** ByteArrayResource с именем файла — иначе multipart-часть не будет распознана как файл. */
    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;
        private final String contentType;

        private NamedByteArrayResource(byte[] bytes, String filename, String contentType) {
            super(bytes);
            this.filename = filename;
            this.contentType = contentType;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        String getContentType() {
            return contentType;
        }
    }
}
