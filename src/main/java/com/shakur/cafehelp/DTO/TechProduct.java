package com.shakur.cafehelp.DTO;

public class TechProduct {
    public int techProductId;
    public String techProductName;
    public ProductDTO Product;
    public Double waste;
    public Double weight;

    public int getTechProductId() {
        return techProductId;
    }

    public void setTechProductId(int techProductId) {
        techProductId = techProductId;
    }

    public String getTechProductName() {
        return techProductName;
    }

    public void setTechProductName(String techProductName) {
        techProductName = techProductName;
    }

    public ProductDTO getProduct() {
        return Product;
    }

    public void setProduct(ProductDTO product) {
        Product = product;
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
