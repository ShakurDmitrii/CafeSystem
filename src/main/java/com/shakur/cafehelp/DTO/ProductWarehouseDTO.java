package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class ProductWarehouseDTO {
    public int productWarehouseId;
    public int productId;
    public int warehouseId;
    public Double quantity;
    public BigDecimal inventoryValue;
    public BigDecimal averageUnitCost;

    public int getProductWarehouseId() {
        return productWarehouseId;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public void setProductWarehouseId(int productWarehouseId) {
        this.productWarehouseId = productWarehouseId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(int warehouseId) {
        this.warehouseId = warehouseId;
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
