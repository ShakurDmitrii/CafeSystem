package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OptimizationResponseDTO {
    private String requestId;
    private String status; // "pending", "running", "completed", "failed"

    // Тайминги
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer estimatedSecondsRemaining;

    // Результаты (только при status = "completed")
    private List<OptimizedRollDTO> results;

    // Статистика алгоритма
    private OptimizationStatsDTO statistics;

    // Ошибки (только при status = "failed")
    private String errorMessage;
    private String errorDetails;

    // Helper methods
    public boolean isCompleted() {
        return "completed".equals(status);
    }

    public boolean isFailed() {
        return "failed".equals(status);
    }
}