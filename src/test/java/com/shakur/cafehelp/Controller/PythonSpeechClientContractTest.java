package com.shakur.cafehelp.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shakur.cafehelp.Controller.PyController.PythonSpeechClient;
import com.shakur.cafehelp.exception.PythonServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonSpeechClientContractTest {

    private PythonSpeechClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new PythonSpeechClient(restTemplate, new ObjectMapper(), "http://python:8000/");
    }

    @Test
    void sendsMultipartAudioWithMenuHintsAndReadsText() {
        server.expect(requestTo("http://python:8000/api/speech/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(request -> {
                    String body = new String(((org.springframework.mock.http.client.MockClientHttpRequest) request)
                            .getBodyAsBytes(), StandardCharsets.UTF_8);
                    assertThat(body).contains("name=\"audio\"; filename=\"phrase.webm\"");
                    assertThat(body).contains("name=\"hints\"");
                    assertThat(body).contains("Филадельфия");
                })
                .andRespond(withSuccess("""
                        {"text": "филадельфия два", "language": "ru", "durationSeconds": 2.1, "confidence": 0.8}
                        """, MediaType.APPLICATION_JSON));

        PythonSpeechClient.Transcription result = client.transcribe(
                "audio".getBytes(StandardCharsets.UTF_8), "phrase.webm", "audio/webm", List.of("Филадельфия"));

        assertThat(result.text()).isEqualTo("филадельфия два");
        assertThat(result.confidence()).isEqualTo(0.8);
        server.verify();
    }

    @Test
    void domainInputErrorIsSurfacedAsInputInvalid() {
        server.expect(requestTo("http://python:8000/api/speech/transcribe"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"detail": {"code": "SPEECH_INPUT_INVALID", "message": "Аудиозапись не читается"}}
                                """));

        assertThatThrownBy(() -> client.transcribe(new byte[]{1}, "a.webm", "audio/webm", List.of()))
                .isInstanceOfSatisfying(PythonServiceException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(PythonServiceException.Code.INPUT_INVALID);
                    assertThat(exception.getMessage()).isEqualTo("Аудиозапись не читается");
                });
    }

    @Test
    void missingModelIsUnavailable() {
        server.expect(requestTo("http://python:8000/api/speech/transcribe"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"detail": {"code": "SPEECH_UNAVAILABLE", "message": "Модель недоступна"}}
                                """));

        assertThatThrownBy(() -> client.transcribe(new byte[]{1}, "a.webm", "audio/webm", List.of()))
                .isInstanceOfSatisfying(PythonServiceException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(PythonServiceException.Code.UNAVAILABLE));
    }

    @Test
    void responseWithoutTextIsInvalid() {
        server.expect(requestTo("http://python:8000/api/speech/transcribe"))
                .andRespond(withSuccess("{\"language\": \"ru\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.transcribe(new byte[]{1}, "a.webm", "audio/webm", List.of()))
                .isInstanceOfSatisfying(PythonServiceException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(PythonServiceException.Code.INVALID_RESPONSE));
    }
}
