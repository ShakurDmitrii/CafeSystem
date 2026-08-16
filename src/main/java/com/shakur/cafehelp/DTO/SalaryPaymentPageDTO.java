package com.shakur.cafehelp.DTO;

import java.util.List;

public record SalaryPaymentPageDTO(
        List<SalaryPaymentDTO> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
