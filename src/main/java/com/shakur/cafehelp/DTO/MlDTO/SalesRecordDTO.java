package com.shakur.cafehelp.DTO.MlDTO;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class SalesRecordDTO {
    private String rollId;
    private String rollName;
    private List<String> ingredients;
    private LocalDate saleDate;
    private Integer quantity;
    private Double totalAmount;
    private Double pricePerUnit;
    private String locationId;
}

