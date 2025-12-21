package com.shakur.cafehelp.DTO;

import java.util.List;

public class ClientWithDutyDTO {
    private ClientDTO client;
    private List<OrderDTO> dutyOrders;
    private Double totalDuty;

    // Конструктор
    public ClientWithDutyDTO(ClientDTO client, List<OrderDTO> dutyOrders) {
        this.client = client;
        this.dutyOrders = dutyOrders;
        this.totalDuty = dutyOrders.stream()
                .mapToDouble(o -> o.getAmount() != null ? o.getAmount() : 0)
                .sum();
    }

    // Геттеры и сеттеры
    public ClientDTO getClient() { return client; }
    public void setClient(ClientDTO client) { this.client = client; }

    public List<OrderDTO> getDutyOrders() { return dutyOrders; }
    public void setDutyOrders(List<OrderDTO> dutyOrders) { this.dutyOrders = dutyOrders; }

    public Double getTotalDuty() { return totalDuty; }
    public void setTotalDuty(Double totalDuty) { this.totalDuty = totalDuty; }
}