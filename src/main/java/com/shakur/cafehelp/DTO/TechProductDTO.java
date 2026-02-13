package com.shakur.cafehelp.DTO;

public class TechProductDTO {

    private Integer techProductId;
    private Integer dishId;
    private Integer productId;
    private Double waste;
    private Double weight;

    public Integer getTechProductId() {
        return techProductId;
    }

    public void setTechProductId(Integer techProductId) {
        this.techProductId = techProductId;
    }

    public Integer getDishId() {
        return dishId;
    }

    public void setDishId(Integer dishId) {
        this.dishId = dishId;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
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