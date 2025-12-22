package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.DTO.MlDTO.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final RestTemplate restTemplate;

    @Value("${ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;

    /**
     * Отправить запрос на предсказание в Python ML сервис
     */
    public RollPredictionResponseDTO predictSales(RollPredictionRequestDTO request) {
        try {
            String url = mlServiceUrl + "/api/ml/predict";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<RollPredictionRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<RollPredictionResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, RollPredictionResponseDTO.class
            );

            log.info("Prediction request sent to ML service: {}", request.getIngredients());
            return response.getBody();

        } catch (Exception e) {
            log.error("Failed to get prediction from ML service: {}", e.getMessage());
            throw new RuntimeException("ML service unavailable: " + e.getMessage());
        }
    }

    /**
     * Пакетное предсказание
     */
    public BatchPredictionResponseDTO batchPredict(BatchPredictionRequestDTO request) {
        try {
            String url = mlServiceUrl + "/api/ml/batch-predict";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<BatchPredictionRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<BatchPredictionResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, BatchPredictionResponseDTO.class
            );

            log.info("Batch prediction for {} rolls sent to ML service",
                    request.getRolls().size());
            return response.getBody();

        } catch (Exception e) {
            log.error("Failed batch prediction: {}", e.getMessage());
            throw new RuntimeException("ML service unavailable: " + e.getMessage());
        }
    }

    /**
     * Оптимизация состава роллов
     */
    public OptimizationResponseDTO optimizeRolls(OptimizationRequestDTO request) {
        try {
            String url = mlServiceUrl + "/api/ml/optimize";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<OptimizationRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<OptimizationResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, OptimizationResponseDTO.class
            );

            log.info("Optimization request sent to ML service: {}", request.getRequestId());
            return response.getBody();

        } catch (Exception e) {
            log.error("Failed optimization request: {}", e.getMessage());
            throw new RuntimeException("ML service unavailable: " + e.getMessage());
        }
    }

    /**
     * Проверить доступность Python ML сервиса
     */
    public boolean isMlServiceAvailable() {
        try {
            String url = mlServiceUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("ML service is not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Локальное предсказание (fallback если Python сервис недоступен)
     */
    public RollPredictionResponseDTO predictLocal(RollPredictionRequestDTO request) {
        log.warn("Using local fallback prediction (ML service unavailable)");

        // Простая эвристика если Python сервис недоступен
        double baseSales = 10.0; // Базовые продажи
        double ingredientBonus = request.getIngredients().size() * 2.0;

        return RollPredictionResponseDTO.builder()
                .predictedSales(baseSales + ingredientBonus)
                .confidenceScore(0.5)
                .ingredients(request.getIngredients())
                .build();
    }
}