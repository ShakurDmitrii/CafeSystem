package com.shakur.cafehelp.DTO;

public class ConsProductDTO {
    public int consignmentId;
    public int productId;
    public int consProductId;
    public Double GROSS;
    public Double quantity;



    public int getConsignmentId() {
        return consignmentId;
    }

    public int getConsProductId() {
        return consProductId;
    }

    public void setConsProductId(int consProductId) {
        this.consProductId = consProductId;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public void setConsignmentId(int consignmentId) {
        this.consignmentId = consignmentId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public Double getGROSS() {
        return GROSS;
    }

    public void setGROSS(Double GROSS) {
        this.GROSS = GROSS;
    }
}
