package com.shakur.cafehelp.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderDishDTO {

    @JsonProperty("dishID")
    public Integer dishID;

    @JsonProperty("setId")
    public Integer setId;

    @JsonProperty("itemType")
    public String itemType;

    @JsonProperty("qty")
    public int qty;

    @JsonProperty("dishName")
    public String dishName;

    @JsonProperty("name")
    public String name;

    @JsonProperty("price")
    public Double price;

    @JsonProperty("sum")
    public Double sum;



    public Integer getDishID() {
        return dishID;
    }

    public void setDishID(Integer dishID) {
        this.dishID = dishID;
    }

    public Integer getSetId() {
        return setId;
    }

    public void setSetId(Integer setId) {
        this.setId = setId;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public String getDishName() {
        return dishName;
    }

    public void setDishName(String dishName) {
        this.dishName = dishName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Double getSum() {
        return sum;
    }

    public void setSum(Double sum) {
        this.sum = sum;
    }
}
