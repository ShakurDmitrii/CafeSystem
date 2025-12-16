package com.shakur.cafehelp.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public class OrderDTO {
    public int orderId;
    public int shiftId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    public LocalDate date;

    public int clientId;
    public Double amount;
    public Boolean status;
    public Boolean type;

    // Добавляем список блюд


    public Boolean getType() { return type; }
    public void setType(Boolean type) { this.type = type; }

    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public int getShiftId() { return shiftId; }
    public void setShiftId(int shiftId) { this.shiftId = shiftId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public int getClientId() { return clientId; }
    public void setClientId(int clientId) { this.clientId = clientId; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

}
