package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class ProductDTO {
    public int productId;
    public int supplierId;
    public String productName;
    public BigDecimal productPrice;
    public Double waste;
    public Boolean isFavorite;
    public String unit;
    public String baseUnit;
    public BigDecimal unitFactor;
    public BigDecimal averageStockPrice;

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

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getBaseUnit() {
        return baseUnit;
    }

    public void setBaseUnit(String baseUnit) {
        this.baseUnit = baseUnit;
    }

    public BigDecimal getUnitFactor() {
        return unitFactor;
    }

    public void setUnitFactor(BigDecimal unitFactor) {
        this.unitFactor = unitFactor;
    }

    public BigDecimal getAverageStockPrice() {
        return averageStockPrice;
    }

    public void setAverageStockPrice(BigDecimal averageStockPrice) {
        this.averageStockPrice = averageStockPrice;
    }
}
