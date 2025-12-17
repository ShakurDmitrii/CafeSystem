package com.shakur.cafehelp.DTO;

public class TechProductDTO {
    public int techProductId;
    public int DishId;
    public int ProductId;
    public Double waste;
    public Double weight;

    public int getTechProductId() {
        return techProductId;
    }

    public void setTechProductId(int techProductId) {
        techProductId = techProductId;
    }

    public int getDishId() {
        return DishId;
    }

    public void setDishId(int dishId) {
        DishId = dishId;
    }

    public int getProductId() {
        return ProductId;
    }

    public void setProductId(int productId) {
        ProductId = productId;
    }

    public void setTechProductName(String techProductName) {
        techProductName = techProductName;
    }

    public int getProduct() {
        return ProductId;
    }

    public void setProduct(int product) {
        ProductId = product;
    }

    public Double getWaste() {
        return waste;
    }

    public void setWaste(Double waste) {
        this.waste = waste;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }
}
