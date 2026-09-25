package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class PreparationWarehouseDTO {
    private Integer preparationWarehouseId;
    private Integer preparationId;
    private Integer warehouseId;
    private Double quantity;
    private BigDecimal inventoryValue;
    private BigDecimal averageUnitCost;

    public Integer getPreparationWarehouseId() {
        return preparationWarehouseId;
    }

    public void setPreparationWarehouseId(Integer preparationWarehouseId) {
        this.preparationWarehouseId = preparationWarehouseId;
    }

    public Integer getPreparationId() {
        return preparationId;
    }

    public void setPreparationId(Integer preparationId) {
        this.preparationId = preparationId;
    }

    public Integer getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Integer warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getInventoryValue() {
        return inventoryValue;
    }

    public void setInventoryValue(BigDecimal inventoryValue) {
        this.inventoryValue = inventoryValue;
    }

    public BigDecimal getAverageUnitCost() {
        return averageUnitCost;
    }

    public void setAverageUnitCost(BigDecimal averageUnitCost) {
        this.averageUnitCost = averageUnitCost;
    }
}
