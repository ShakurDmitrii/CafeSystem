package com.shakur.cafehelp.DTO;

public class InventoryShiftReportRowDTO {
    private Integer productId;
    private String productName;
    private String unit;
    private Double openingQty;
    private Double movementInQty;
    private Double movementOutQty;
    private Double movementNetQty;
    private Double soldQty;
    private Double expectedQty;
    private Double systemQty;
    private Double actualQty;
    private Double discrepancyQty;
    private Double shortageQty;
    private Boolean shortageFlag;

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Double getOpeningQty() {
        return openingQty;
    }

    public void setOpeningQty(Double openingQty) {
        this.openingQty = openingQty;
    }

    public Double getMovementInQty() {
        return movementInQty;
    }

    public void setMovementInQty(Double movementInQty) {
        this.movementInQty = movementInQty;
    }

    public Double getMovementOutQty() {
        return movementOutQty;
    }

    public void setMovementOutQty(Double movementOutQty) {
        this.movementOutQty = movementOutQty;
    }

    public Double getMovementNetQty() {
        return movementNetQty;
    }

    public void setMovementNetQty(Double movementNetQty) {
        this.movementNetQty = movementNetQty;
    }

    public Double getSoldQty() {
        return soldQty;
    }

    public void setSoldQty(Double soldQty) {
        this.soldQty = soldQty;
    }

    public Double getExpectedQty() {
        return expectedQty;
    }

    public void setExpectedQty(Double expectedQty) {
        this.expectedQty = expectedQty;
    }

    public Double getSystemQty() {
        return systemQty;
    }

    public void setSystemQty(Double systemQty) {
        this.systemQty = systemQty;
    }

    public Double getActualQty() {
        return actualQty;
    }

    public void setActualQty(Double actualQty) {
        this.actualQty = actualQty;
    }

    public Double getDiscrepancyQty() {
        return discrepancyQty;
    }

    public void setDiscrepancyQty(Double discrepancyQty) {
        this.discrepancyQty = discrepancyQty;
    }

    public Double getShortageQty() {
        return shortageQty;
    }

    public void setShortageQty(Double shortageQty) {
        this.shortageQty = shortageQty;
    }

    public Boolean getShortageFlag() {
        return shortageFlag;
    }

    public void setShortageFlag(Boolean shortageFlag) {
        this.shortageFlag = shortageFlag;
    }
}
