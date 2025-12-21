package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OrderDebtDTO {
    private Long orderId;
    private Long clientId; // можно добавить ID клиента для удобства
    private LocalDate orderDate;
    private BigDecimal amount;
    private LocalDate debtPaymentDate; // Дата погашения долга
    private boolean status; // Статус заказа (готов/не готов)
    private Integer daysOverdue; // Дней просрочки
    private Integer shiftId; // ID смены
    private Boolean orderType; // Тип заказа (доставка/по месту)

    // Конструкторы
    public OrderDebtDTO() {
    }

    public OrderDebtDTO(Long orderId, LocalDate orderDate, BigDecimal amount,
                        LocalDate debtPaymentDate, boolean status, int daysOverdue) {
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.amount = amount;
        this.debtPaymentDate = debtPaymentDate;
        this.status = status;
        this.daysOverdue = daysOverdue;
    }

    // Полный конструктор
    public OrderDebtDTO(Long orderId, Long clientId, LocalDate orderDate, BigDecimal amount,
                        LocalDate debtPaymentDate, boolean status, int daysOverdue,
                        Integer shiftId, Boolean orderType) {
        this.orderId = orderId;
        this.clientId = clientId;
        this.orderDate = orderDate;
        this.amount = amount;
        this.debtPaymentDate = debtPaymentDate;
        this.status = status;
        this.daysOverdue = daysOverdue;
        this.shiftId = shiftId;
        this.orderType = orderType;
    }

    // Геттеры и сеттеры
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDebtPaymentDate() {
        return debtPaymentDate;
    }

    public void setDebtPaymentDate(LocalDate debtPaymentDate) {
        this.debtPaymentDate = debtPaymentDate;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public Integer getDaysOverdue() {
        return daysOverdue;
    }

    public void setDaysOverdue(Integer daysOverdue) {
        this.daysOverdue = daysOverdue;
    }

    public Integer getShiftId() {
        return shiftId;
    }

    public void setShiftId(Integer shiftId) {
        this.shiftId = shiftId;
    }

    public Boolean getOrderType() {
        return orderType;
    }

    public void setOrderType(Boolean orderType) {
        this.orderType = orderType;
    }

    // Вспомогательные методы
    public boolean isOverdue() {
        if (debtPaymentDate == null) {
            return false;
        }
        return LocalDate.now().isAfter(debtPaymentDate);
    }

    public String getStatusString() {
        return status ? "Готов" : "Готовится";
    }

    public String getOrderTypeString() {
        if (orderType == null) {
            return "Не указан";
        }
        return orderType ? "Доставка" : "По месту";
    }

    // Форматированная строка для отображения
    public String getFormattedInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Заказ #").append(orderId)
                .append(" от ").append(orderDate)
                .append(" на сумму: ").append(amount).append(" руб.");

        if (debtPaymentDate != null) {
            sb.append("\nДата погашения: ").append(debtPaymentDate);
            if (isOverdue() && daysOverdue > 0) {
                sb.append(" (просрочено на ").append(daysOverdue).append(" дн.)");
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "OrderDebtDTO{" +
                "orderId=" + orderId +
                ", clientId=" + clientId +
                ", orderDate=" + orderDate +
                ", amount=" + amount +
                ", debtPaymentDate=" + debtPaymentDate +
                ", status=" + status +
                ", daysOverdue=" + daysOverdue +
                ", shiftId=" + shiftId +
                ", orderType=" + orderType +
                '}';
    }
}