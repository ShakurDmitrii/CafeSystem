package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class BatchPredictionRequestDTO {
    private List<RollPredictionRequestDTO> rolls;
    private String requestId;
    private LocalDateTime timestamp;
}