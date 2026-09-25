package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.Controller.PyController.MlPredictionController;
import com.shakur.cafehelp.Service.MlServices.PredictionService;
import com.shakur.cafehelp.exception.PythonServiceException;
import com.shakur.cafehelp.exception.PythonServiceExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.SocketTimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PythonServiceFailureMappingTest {

    @Test
    void predictionTimeoutIsGatewayTimeoutRatherThanBadRequest() throws Exception {
        PredictionService service = mock(PredictionService.class);
        when(service.predictSales(any())).thenThrow(
                PythonServiceException.timeout(new SocketTimeoutException("upstream address"))
        );
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new MlPredictionController(service))
                .setControllerAdvice(new PythonServiceExceptionHandler())
                .build();

        mockMvc.perform(post("/api/ml/predict/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredients\":[\"rice\"]}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PYTHON_TIMEOUT"))
                .andExpect(jsonPath("$.message").value("Сервис аналитики не ответил вовремя"));
    }
}
