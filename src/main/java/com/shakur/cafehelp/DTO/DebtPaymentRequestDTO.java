package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class DebtPaymentRequestDTO {
    private BigDecimal amount;
    private String paymentType;
    private String idempotencyKey;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
