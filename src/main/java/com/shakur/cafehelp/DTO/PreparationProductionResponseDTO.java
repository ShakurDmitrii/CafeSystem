package com.shakur.cafehelp.DTO;

public class PreparationProductionResponseDTO {
    private Integer preparationId;
    private String preparationName;
    private Integer warehouseId;
    private Double batchCount;
    private Double producedQuantity;
    private Double warehouseQuantityAfter;

    public Integer getPreparationId() {
        return preparationId;
    }

    public void setPreparationId(Integer preparationId) {
        this.preparationId = preparationId;
    }

    public String getPreparationName() {
        return preparationName;
    }

    public void setPreparationName(String preparationName) {
        this.preparationName = preparationName;
    }

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

    public Double getProducedQuantity() {
        return producedQuantity;
    }

    public void setProducedQuantity(Double producedQuantity) {
        this.producedQuantity = producedQuantity;
    }

    public Double getWarehouseQuantityAfter() {
        return warehouseQuantityAfter;
    }

    public void setWarehouseQuantityAfter(Double warehouseQuantityAfter) {
        this.warehouseQuantityAfter = warehouseQuantityAfter;
    }
}
