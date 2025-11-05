package com.shakur.cafehelp.DTO;

public class ClientDishDTO {
    public int dishId;
    public int clientId;
    public String dishName;

    public int getDishId() {
        return dishId;
    }

    public void setDishId(int dishId) {
        this.dishId = dishId;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }
}
