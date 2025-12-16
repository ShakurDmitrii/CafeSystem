package com.shakur.cafehelp.DTO;

public class DishDTO {
    public int dishId;
    public String dishName;
    public Double weight;
    public Double firstCost;
    public Double price;
    public int techProduct;
    public int qty;

    public int getTechProduct() {
        return techProduct;
    }

    public void setTechProduct(int techProduct) {
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
