package com.shakur.cafehelp.Controller.PyController;

import com.shakur.cafehelp.DTO.MlDTO.*;
import com.shakur.cafehelp.Service.MlServices.PredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ml/predict")
@RequiredArgsConstructor
public class MlPredictionController {

    private final PredictionService predictionService;

    /**
     * 1. Предсказать продажи для одного ролла
     */
    @PostMapping("/single")
    public ResponseEntity<RollPredictionResponseDTO> predictSingle(
            @RequestBody RollPredictionRequestDTO request) {

        try {
            var result = predictionService.predictSales(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    RollPredictionResponseDTO.builder()
                            .errorMessage(e.getMessage())
                            .build()
            );
        }
    }

    /**
     * 2. Пакетное предсказание
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchPredictionResponseDTO> predictBatch(
            @RequestBody BatchPredictionRequestDTO request) {

        try {
            var results = predictionService.batchPredict(request);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    BatchPredictionResponseDTO.builder()
                            .errorMessage(e.getMessage())
                            .build()
            );
        }
    }

    /**
     * 3. Оптимизация состава роллов
     */
    @PostMapping("/optimize")
    public ResponseEntity<OptimizationResponseDTO> optimizeRolls(
            @RequestBody OptimizationRequestDTO request) {

        try {
            var result = predictionService.optimizeRolls(request);
            return ResponseEntity.accepted().body(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    OptimizationResponseDTO.builder()
                            .errorMessage(e.getMessage())
                            .status("failed")
                            .build()
            );
        }
    }

    @PostMapping("/generate-dish")
    public ResponseEntity<Map<String, Object>> generateDish(
            @RequestBody(required = false) GenerateDishRequestDTO request) {
        try {
            GenerateDishRequestDTO effectiveRequest = request != null ? request : new GenerateDishRequestDTO();
            return ResponseEntity.ok(predictionService.generateNewDish(effectiveRequest));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "status", "failed",
                            "errorMessage", e.getMessage()
                    )
            );
        }
    }

    @PostMapping("/generate-dish/save")
    public ResponseEntity<?> saveGeneratedDish(@RequestBody SaveGeneratedDishRequestDTO request) {
        try {
            return ResponseEntity.ok(predictionService.saveGeneratedDish(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("status", "failed", "errorMessage", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("status", "failed", "errorMessage", e.getMessage())
            );
        }
    }
}
