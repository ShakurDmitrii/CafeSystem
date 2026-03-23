package com.shakur.cafehelp.DTO;

public class PreparationDTO {
    private Integer preparationId;
    private String preparationName;
    private Double outputWeight;
    private Double cost;

    public Integer getPreparationId() {
        return preparationId;
    }

    public void setPreparationId(Integer preparationId) {
        this.preparationId = preparationId;
    }

    public String getPreparationName() {
        return preparationName;
    }

    public void setPreparationName(String preparationName) {
        this.preparationName = preparationName;
    }

    public Double getOutputWeight() {
        return outputWeight;
    }

    public void setOutputWeight(Double outputWeight) {
        this.outputWeight = outputWeight;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }
}
