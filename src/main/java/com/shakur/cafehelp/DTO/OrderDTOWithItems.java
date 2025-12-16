package com.shakur.cafehelp.DTO;

import java.util.List;

public class OrderDTOWithItems extends OrderDTO {
    // Список блюд, каждое с dishId и qty
    private List<OrderDishDTO> items;

    public List<OrderDishDTO> getItems() { return items; }
    public void setItems(List<OrderDishDTO> items) { this.items = items; }


    @Override
    public int getOrderId() {
        return orderId;
    }

    @Override
    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }


}