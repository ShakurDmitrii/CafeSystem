package com.shakur.cafehelp.DTO.MlDTO;

import lombok.Data;

import java.util.List;

// Для пагинации
@Data
public class SalesPageDTO {
    private List<SalesRecordDTO> content;
    private int pageNumber;
    private int pageSize;
    private int totalPages;
    private long totalElements;
}