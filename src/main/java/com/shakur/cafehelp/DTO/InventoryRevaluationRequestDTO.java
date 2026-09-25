package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class InventoryRevaluationRequestDTO {
    private BigDecimal averageUnitCost;
    private String reason;

    public BigDecimal getAverageUnitCost() {
        return averageUnitCost;
    }

    public void setAverageUnitCost(BigDecimal averageUnitCost) {
        this.averageUnitCost = averageUnitCost;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

}
