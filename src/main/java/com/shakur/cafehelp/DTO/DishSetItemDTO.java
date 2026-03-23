package com.shakur.cafehelp.DTO;

public class DishSetItemDTO {
    private Integer setItemId;
    private Integer setId;
    private Integer dishId;
    private String dishName;
    private Double dishPrice;
    private Double dishFirstCost;
    private String imageUrl;
    private String categoryName;
    private Integer qty;

    public Integer getSetItemId() {
        return setItemId;
    }

    public void setSetItemId(Integer setItemId) {
        this.setItemId = setItemId;
    }

    public Integer getSetId() {
        return setId;
    }

    public void setSetId(Integer setId) {
        this.setId = setId;
    }

    public Integer getDishId() {
        return dishId;
    }

    public void setDishId(Integer dishId) {
        this.dishId = dishId;
    }

    public String getDishName() {
        return dishName;
    }

    public void setDishName(String dishName) {
        this.dishName = dishName;
    }

    public Double getDishPrice() {
        return dishPrice;
    }

    public void setDishPrice(Double dishPrice) {
        this.dishPrice = dishPrice;
    }

    public Double getDishFirstCost() {
        return dishFirstCost;
    }

    public void setDishFirstCost(Double dishFirstCost) {
        this.dishFirstCost = dishFirstCost;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }
}
