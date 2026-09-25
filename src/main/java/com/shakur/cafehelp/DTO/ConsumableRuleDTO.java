package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class ConsumableRuleDTO {
    private Integer productId;
    private String productName;
    private String itemType;
    private String unit;
    private String baseUnit;
    private BigDecimal unitFactor;
    private String basis;
    private BigDecimal defaultQuantity;
    private BigDecimal triggerQuantity;
    private Integer dishCategoryId;
    private String dishCategoryName;
    private Boolean active;

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getBaseUnit() { return baseUnit; }
    public void setBaseUnit(String baseUnit) { this.baseUnit = baseUnit; }
    public BigDecimal getUnitFactor() { return unitFactor; }
    public void setUnitFactor(BigDecimal unitFactor) { this.unitFactor = unitFactor; }
    public String getBasis() { return basis; }
    public void setBasis(String basis) { this.basis = basis; }
    public BigDecimal getDefaultQuantity() { return defaultQuantity; }
    public void setDefaultQuantity(BigDecimal defaultQuantity) { this.defaultQuantity = defaultQuantity; }
    public BigDecimal getTriggerQuantity() { return triggerQuantity; }
    public void setTriggerQuantity(BigDecimal triggerQuantity) { this.triggerQuantity = triggerQuantity; }
    public Integer getDishCategoryId() { return dishCategoryId; }
    public void setDishCategoryId(Integer dishCategoryId) { this.dishCategoryId = dishCategoryId; }
    public String getDishCategoryName() { return dishCategoryName; }
    public void setDishCategoryName(String dishCategoryName) { this.dishCategoryName = dishCategoryName; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
