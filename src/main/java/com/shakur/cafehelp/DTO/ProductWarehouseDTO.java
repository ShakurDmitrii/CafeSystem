package com.shakur.cafehelp.DTO;

public class ProductWarehouseDTO {
    public int productWarehouseId;
    public int productId;
    public int warehouseId;
    public Double quantity;

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
}
