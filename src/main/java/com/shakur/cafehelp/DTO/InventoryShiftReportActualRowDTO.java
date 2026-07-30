package com.shakur.cafehelp.DTO;

public class InventoryShiftReportActualRowDTO {
    private Integer productId;
    private Double actualQty;

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Double getActualQty() {
        return actualQty;
    }

    public void setActualQty(Double actualQty) {
        this.actualQty = actualQty;
    }
}
