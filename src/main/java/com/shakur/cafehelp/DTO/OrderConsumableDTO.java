package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class OrderConsumableDTO {
    private Integer productId;
    private String productName;
    private String itemType;
    private String baseUnit;
    private BigDecimal suggestedQuantity;
    private BigDecimal actualQuantity;
    private BigDecimal surchargeAmount;
    private BigDecimal inventoryCost;
    private Boolean manualOverride;

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public String getBaseUnit() { return baseUnit; }
    public void setBaseUnit(String baseUnit) { this.baseUnit = baseUnit; }
    public BigDecimal getSuggestedQuantity() { return suggestedQuantity; }
    public void setSuggestedQuantity(BigDecimal suggestedQuantity) { this.suggestedQuantity = suggestedQuantity; }
    public BigDecimal getActualQuantity() { return actualQuantity; }
    public void setActualQuantity(BigDecimal actualQuantity) { this.actualQuantity = actualQuantity; }
    public BigDecimal getSurchargeAmount() { return surchargeAmount; }
    public void setSurchargeAmount(BigDecimal surchargeAmount) { this.surchargeAmount = surchargeAmount; }
    public BigDecimal getInventoryCost() { return inventoryCost; }
    public void setInventoryCost(BigDecimal inventoryCost) { this.inventoryCost = inventoryCost; }
    public Boolean getManualOverride() { return manualOverride; }
    public void setManualOverride(Boolean manualOverride) { this.manualOverride = manualOverride; }
}
