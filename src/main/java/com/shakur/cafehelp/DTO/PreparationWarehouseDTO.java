package com.shakur.cafehelp.DTO;

public class PreparationWarehouseDTO {
    private Integer preparationWarehouseId;
    private Integer preparationId;
    private Integer warehouseId;
    private Double quantity;

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
}
