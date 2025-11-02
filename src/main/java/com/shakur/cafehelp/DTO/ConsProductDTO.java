package com.shakur.cafehelp.DTO;

public class ConsProductDTO {
    public int consignmentId;
    public int productId;
    public Double GROSS;

    public int getConsignmentId() {
        return consignmentId;
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
