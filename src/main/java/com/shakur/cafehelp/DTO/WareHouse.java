package com.shakur.cafehelp.DTO;

public class WareHouse {
    public int warehouseId;
    public String warehouseName;
    public int productWarehouse;

    public int getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(int warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public int getWarehouseProduct() {
        return productWarehouse;
    }

    public void setWarehouseProduct(int productWarehouse) {
        this.productWarehouse = productWarehouse;
    }
}
