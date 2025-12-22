package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BatchPredictionResponseDTO {
    private String requestId;
    private LocalDateTime timestamp;
    private List<RollPredictionResponseDTO> predictions;
    private String modelVersion;
    private String status; // "success", "partial", "failed"
    private Integer totalProcessed;
    private Integer failedCount;
    private String errorMessage;

    // Helper methods
    public boolean isSuccess() {
        return "success".equals(status);
    }
}