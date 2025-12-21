package com.shakur.cafehelp.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    public Boolean duty;
    public Double time;
    public Double timeDelay;
    public LocalDateTime created_at;


    // Добавляем список блюд
    public Double getTimeDelay() {
        return timeDelay;
    }

    public Boolean getDuty() {
        return duty;
    }

    public void setDuty(Boolean duty) {
        this.duty = duty;
    }

    public LocalDateTime getCreated_at() {
        return created_at;
    }

    public void setCreated_at(LocalDateTime created_at) {
        this.created_at = created_at;
    }

    public void setTimeDelay(Double timeDelay) {
        this.timeDelay = timeDelay;
    }

    public Double getTime() {
        return time;
    }

    public void setTime(Double time) {
        this.time = time;
    }

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
