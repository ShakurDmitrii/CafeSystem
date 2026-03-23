package com.shakur.cafehelp.DTO;

public class PreparationProductionRequestDTO {
    private Integer warehouseId;
    private Double batchCount;

    public Integer getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Integer warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Double getBatchCount() {
        return batchCount;
    }

    public void setBatchCount(Double batchCount) {
        this.batchCount = batchCount;
    }
}
