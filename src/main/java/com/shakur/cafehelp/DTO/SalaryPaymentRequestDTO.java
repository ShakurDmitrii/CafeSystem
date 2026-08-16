package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class SalaryPaymentRequestDTO {
    private BigDecimal amount;
    private String idempotencyKey;
    private String comment;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
