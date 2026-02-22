package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class MovementTurnoverRowDTO {
    private Integer productId;
    private String productName;
    private BigDecimal qtyIn;
    private BigDecimal qtyOutMovement;
    private BigDecimal qtyWriteoff;
    private BigDecimal qtyOutTotal;
    private BigDecimal amountIn;
    private BigDecimal amountOutMovement;
    private BigDecimal amountWriteoff;
    private BigDecimal amountOutTotal;

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

    public BigDecimal getQtyIn() {
        return qtyIn;
    }

    public void setQtyIn(BigDecimal qtyIn) {
        this.qtyIn = qtyIn;
    }

    public BigDecimal getQtyOutMovement() {
        return qtyOutMovement;
    }

    public void setQtyOutMovement(BigDecimal qtyOutMovement) {
        this.qtyOutMovement = qtyOutMovement;
    }

    public BigDecimal getQtyWriteoff() {
        return qtyWriteoff;
    }

    public void setQtyWriteoff(BigDecimal qtyWriteoff) {
        this.qtyWriteoff = qtyWriteoff;
    }

    public BigDecimal getQtyOutTotal() {
        return qtyOutTotal;
    }

    public void setQtyOutTotal(BigDecimal qtyOutTotal) {
        this.qtyOutTotal = qtyOutTotal;
    }

    public BigDecimal getAmountIn() {
        return amountIn;
    }

    public void setAmountIn(BigDecimal amountIn) {
        this.amountIn = amountIn;
    }

    public BigDecimal getAmountOutMovement() {
        return amountOutMovement;
    }

    public void setAmountOutMovement(BigDecimal amountOutMovement) {
        this.amountOutMovement = amountOutMovement;
    }

    public BigDecimal getAmountWriteoff() {
        return amountWriteoff;
    }

    public void setAmountWriteoff(BigDecimal amountWriteoff) {
        this.amountWriteoff = amountWriteoff;
    }

    public BigDecimal getAmountOutTotal() {
        return amountOutTotal;
    }

    public void setAmountOutTotal(BigDecimal amountOutTotal) {
        this.amountOutTotal = amountOutTotal;
    }
}
