package com.shakur.cafehelp.DTO;

import java.util.List;

public class OrderEditRequestDTO {
    private Integer expectedVersion;
    private Integer clientId;
    private Boolean type;
    private Double time;
    private Double timeDelay;
    private Double deliveryCost;
    private String deliveryPhone;
    private String deliveryAddress;
    private List<OrderDishDTO> items;

    public Integer getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Integer expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    public Boolean getType() {
        return type;
    }

    public void setType(Boolean type) {
        this.type = type;
    }

    public Double getTime() {
        return time;
    }

    public void setTime(Double time) {
        this.time = time;
    }

    public Double getTimeDelay() {
        return timeDelay;
    }

    public void setTimeDelay(Double timeDelay) {
        this.timeDelay = timeDelay;
    }

    public Double getDeliveryCost() {
        return deliveryCost;
    }

    public void setDeliveryCost(Double deliveryCost) {
        this.deliveryCost = deliveryCost;
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

    public List<OrderDishDTO> getItems() {
        return items;
    }

    public void setItems(List<OrderDishDTO> items) {
        this.items = items;
    }
}

