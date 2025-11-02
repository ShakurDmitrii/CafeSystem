package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ConsignmentNoteDTO {
    public int consignmentId;
    public int supplierId;
    public BigDecimal amount;
    public LocalDateTime date;


    public int getConsignmentId() {
        return consignmentId;
    }

    public void setConsignmentId(int consignmentId) {
        this.consignmentId = consignmentId;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(int supplierId) {
        this.supplierId = supplierId;
    }



    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

}
