package com.shakur.cafehelp.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

public class OrderDTO {
    public int orderId;
    public int shiftId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    public LocalDate date;

    public Integer clientId;
    public Double amount;
    public Boolean status;
    public Boolean type;
    public Boolean duty;
    public Double time;
    public Double timeDelay;
    public LocalDateTime created_at;
    public LocalDate debt_payment_date;
    public LocalDate date_issue;
    public String deliveryPhone;
    public String deliveryAddress;
    public String paymentType;
    public Boolean paid;
    public BigDecimal debtOriginalAmount;
    public BigDecimal debtRemainingAmount;
    public LocalDateTime cancelledAt;
    public String cancelReason;
    public Integer version;
    public List<OrderDishDTO> items;

    public LocalDate getDebt_payment_date() {
        return debt_payment_date;
    }

    public void setDebt_payment_date(LocalDate debt_payment_date) {
        this.debt_payment_date = debt_payment_date;
    }

    public LocalDate getDate_issue() {
        return date_issue;
    }

    public void setDate_issue(LocalDate date_issue) {
        this.date_issue = date_issue;
    }

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

    public Boolean getType() {
        return type;
    }

    public void setType(Boolean type) {
        this.type = type;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public int getShiftId() {
        return shiftId;
    }

    public void setShiftId(int shiftId) {
        this.shiftId = shiftId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getDeliveryPhone() {
        return deliveryPhone;
    }

    public void setDeliveryPhone(String deliveryPhone) {
        this.deliveryPhone = deliveryPhone;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public Boolean getPaid() {
        return paid;
    }

    public void setPaid(Boolean paid) {
        this.paid = paid;
    }

    public BigDecimal getDebtOriginalAmount() { return debtOriginalAmount; }
    public void setDebtOriginalAmount(BigDecimal debtOriginalAmount) { this.debtOriginalAmount = debtOriginalAmount; }
    public BigDecimal getDebtRemainingAmount() { return debtRemainingAmount; }
    public void setDebtRemainingAmount(BigDecimal debtRemainingAmount) { this.debtRemainingAmount = debtRemainingAmount; }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public List<OrderDishDTO> getItems() {
        return items;
    }

    public void setItems(List<OrderDishDTO> items) {
        this.items = items;
    }

}
