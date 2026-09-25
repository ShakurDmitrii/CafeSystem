package com.shakur.cafehelp.DTO;

import java.util.List;

public class ConsumablePreviewRequestDTO {
    private Integer personCount;
    private List<OrderDishDTO> items;
    private List<OrderConsumableDTO> consumables;

    public Integer getPersonCount() { return personCount; }
    public void setPersonCount(Integer personCount) { this.personCount = personCount; }
    public List<OrderDishDTO> getItems() { return items; }
    public void setItems(List<OrderDishDTO> items) { this.items = items; }
    public List<OrderConsumableDTO> getConsumables() { return consumables; }
    public void setConsumables(List<OrderConsumableDTO> consumables) { this.consumables = consumables; }
}
