package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.exception.PythonServiceException;
import com.shakur.cafehelp.exception.PythonServiceExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class MlValidationErrorTest {
    @Test
    void explicitDomainErrorReachesUserWithoutBecomingBadGateway() {
        var error = HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_ENTITY, "invalid",
                new HttpHeaders(), """
                {"detail":{"code":"ML_INPUT_INVALID","message":"Недостаточно данных для обучения"}}
                """.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        var mapped = new PythonServiceExceptionHandler().handlePythonServiceException(PythonServiceException.translate(error));
        assertThat(mapped.getStatusCode().value()).isEqualTo(422);
        assertThat(mapped.getBody().get("message")).isEqualTo("Недостаточно данных для обучения");
    }

    @Test
    void arbitraryUpstreamDetailsAreNotExposed() {
        var error = HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_ENTITY, "invalid",
                new HttpHeaders(), "{\"detail\":\"private backend configuration\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertThat(PythonServiceException.translate(error).getCode()).isEqualTo(PythonServiceException.Code.UPSTREAM_ERROR);
    }
}
