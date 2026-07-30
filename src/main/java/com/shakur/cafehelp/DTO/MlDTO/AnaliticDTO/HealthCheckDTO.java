package com.shakur.cafehelp.DTO.MlDTO.AnaliticDTO;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

// HealthCheckDTO.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckDTO {
    private String status;
    private String pythonService;
    private LocalDateTime timestamp;
    private String message;
}
