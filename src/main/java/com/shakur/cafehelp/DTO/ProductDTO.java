package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class ProductDTO {
    public int productId;
    public int supplierId;
    public String productName;
    public BigDecimal productPrice;
    public Double waste;
    public Boolean isFavorite;

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(int supplierId) {
        this.supplierId = supplierId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
    }

    public Double getWaste() {
        return waste;
    }

    public Boolean getFavorite() {
        return isFavorite;
    }

    public void setFavorite(Boolean favorite) {
        isFavorite = favorite;
    }

    public void setWaste(Double waste) {
        this.waste = waste;
    }
}
