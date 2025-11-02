package com.shakur.cafehelp.DTO;

import java.util.List;

public class Dish {
    public int dishId;
    public String dishName;
    public Double weight;
    public Double firstCost;
    public Double price;
    public TechProduct techProduct;

    public TechProduct getTechProduct() {
        return techProduct;
    }

    public void setTechProduct(TechProduct techProduct) {
        this.techProduct = techProduct;
    }

    public int getDishId() {
        return dishId;
    }

    public void setDishId(int dishId) {
        this.dishId = dishId;
    }

    public String getDishName() {
        return dishName;
    }

    public void setDishName(String dishName) {
        this.dishName = dishName;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Double getFirstCost() {
        return firstCost;
    }

    public void setFirstCost(Double firstCost) {
        this.firstCost = firstCost;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

}
