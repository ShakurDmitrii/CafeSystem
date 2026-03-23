package com.shakur.cafehelp.DTO;

import java.util.ArrayList;
import java.util.List;

public class DishSetDTO {
    private Integer setId;
    private String setName;
    private Double price;
    private Double firstCost;
    private String imageUrl;
    private List<DishSetItemDTO> items = new ArrayList<>();

    public Integer getSetId() {
        return setId;
    }

    public void setSetId(Integer setId) {
        this.setId = setId;
    }

    public String getSetName() {
        return setName;
    }

    public void setSetName(String setName) {
        this.setName = setName;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Double getFirstCost() {
        return firstCost;
    }

    public void setFirstCost(Double firstCost) {
        this.firstCost = firstCost;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public List<DishSetItemDTO> getItems() {
        return items;
    }

    public void setItems(List<DishSetItemDTO> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
}
