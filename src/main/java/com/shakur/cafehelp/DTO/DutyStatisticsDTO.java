package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class DutyStatisticsDTO {
    private BigDecimal totalDebtAmount;
    private Integer clientCount;
    private Integer orderCount;
    private BigDecimal averageDebtPerClient;

    // Конструкторы
    public DutyStatisticsDTO() {}

    // Геттеры и сеттеры
    public BigDecimal getTotalDebtAmount() { return totalDebtAmount; }
    public void setTotalDebtAmount(BigDecimal totalDebtAmount) { this.totalDebtAmount = totalDebtAmount; }

    public Integer getClientCount() { return clientCount; }
    public void setClientCount(Integer clientCount) { this.clientCount = clientCount; }

    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }

    public BigDecimal getAverageDebtPerClient() { return averageDebtPerClient; }
    public void setAverageDebtPerClient(BigDecimal averageDebtPerClient) { this.averageDebtPerClient = averageDebtPerClient; }
}