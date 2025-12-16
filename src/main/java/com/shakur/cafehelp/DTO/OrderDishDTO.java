package com.shakur.cafehelp.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderDishDTO {

    @JsonProperty("dishID")
    public int dishID;

    @JsonProperty("qty")
    public int qty;



    public int getDishID() {
        return dishID;
    }

    public void setDishID(int dishID) {
        this.dishID = dishID;
    }


    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }
}
