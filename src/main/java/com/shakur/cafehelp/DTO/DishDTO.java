package com.shakur.cafehelp.DTO;

public class DishDTO {
    public int dishId;
    public String dishName;
    public String category;
    public Integer categoryId;
    public String categoryName;
    public Double weight;
    public Double firstCost;
    public Double price;
    public Integer techProduct;
    public int qty;
    public String imageUrl;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public Integer getTechProduct() {
        return techProduct;
    }

    public void setTechProduct(Integer techProduct) {
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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

}
