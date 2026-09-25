package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class ProductDTO {
    public int productId;
    public Integer supplierId;
    public String productName;
    public BigDecimal productPrice;
    public Double waste;
    public Boolean isFavorite;
    public String unit;
    public String baseUnit;
    public BigDecimal unitFactor;
    public BigDecimal averageStockPrice;
    public BigDecimal supplierPrice;
    public String supplierUnit;
    public BigDecimal supplierUnitFactor;
    public String supplierSku;
    public String imageUrl;
    public String itemType;
    public String consumableBasis;
    public BigDecimal consumableDefaultQuantity;
    public BigDecimal consumableTriggerQuantity;
    public Integer consumableDishCategoryId;
    public Boolean consumableActive;

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public Integer getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Integer supplierId) {
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

    public BigDecimal getSupplierPrice() {
        return supplierPrice;
    }

    public void setSupplierPrice(BigDecimal supplierPrice) {
        this.supplierPrice = supplierPrice;
    }

    public String getSupplierUnit() {
        return supplierUnit;
    }

    public void setSupplierUnit(String supplierUnit) {
        this.supplierUnit = supplierUnit;
    }

    public BigDecimal getSupplierUnitFactor() {
        return supplierUnitFactor;
    }

    public void setSupplierUnitFactor(BigDecimal supplierUnitFactor) {
        this.supplierUnitFactor = supplierUnitFactor;
    }

    public String getSupplierSku() {
        return supplierSku;
    }

    public void setSupplierSku(String supplierSku) {
        this.supplierSku = supplierSku;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public String getConsumableBasis() { return consumableBasis; }
    public void setConsumableBasis(String consumableBasis) { this.consumableBasis = consumableBasis; }
    public BigDecimal getConsumableDefaultQuantity() { return consumableDefaultQuantity; }
    public void setConsumableDefaultQuantity(BigDecimal value) { this.consumableDefaultQuantity = value; }
    public BigDecimal getConsumableTriggerQuantity() { return consumableTriggerQuantity; }
    public void setConsumableTriggerQuantity(BigDecimal value) { this.consumableTriggerQuantity = value; }
    public Integer getConsumableDishCategoryId() { return consumableDishCategoryId; }
    public void setConsumableDishCategoryId(Integer value) { this.consumableDishCategoryId = value; }
    public Boolean getConsumableActive() { return consumableActive; }
    public void setConsumableActive(Boolean value) { this.consumableActive = value; }
}
