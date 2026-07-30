package com.shakur.cafehelp.DTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class InventoryShiftReportDTO {
    private Integer reportId;
    private Integer warehouseId;
    private String warehouseName;
    private Integer shiftId;
    private LocalDate shiftDate;
    private LocalTime shiftStartTime;
    private LocalTime shiftEndTime;
    private Integer ordersCount;
    private Integer soldPositionsCount;
    private Integer soldItemsCount;
    private Boolean snapshotAvailable;
    private Boolean saved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime appliedAt;
    private List<InventoryShiftSaleItemDTO> sales;
    private List<InventoryShiftReportRowDTO> rows;

    public Integer getReportId() {
        return reportId;
    }

    public void setReportId(Integer reportId) {
        this.reportId = reportId;
    }

    public Integer getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Integer warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public Integer getShiftId() {
        return shiftId;
    }

    public void setShiftId(Integer shiftId) {
        this.shiftId = shiftId;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public void setShiftDate(LocalDate shiftDate) {
        this.shiftDate = shiftDate;
    }

    public LocalTime getShiftStartTime() {
        return shiftStartTime;
    }

    public void setShiftStartTime(LocalTime shiftStartTime) {
        this.shiftStartTime = shiftStartTime;
    }

    public LocalTime getShiftEndTime() {
        return shiftEndTime;
    }

    public void setShiftEndTime(LocalTime shiftEndTime) {
        this.shiftEndTime = shiftEndTime;
    }

    public Integer getOrdersCount() {
        return ordersCount;
    }

    public void setOrdersCount(Integer ordersCount) {
        this.ordersCount = ordersCount;
    }

    public Integer getSoldPositionsCount() {
        return soldPositionsCount;
    }

    public void setSoldPositionsCount(Integer soldPositionsCount) {
        this.soldPositionsCount = soldPositionsCount;
    }

    public Integer getSoldItemsCount() {
        return soldItemsCount;
    }

    public void setSoldItemsCount(Integer soldItemsCount) {
        this.soldItemsCount = soldItemsCount;
    }

    public Boolean getSnapshotAvailable() {
        return snapshotAvailable;
    }

    public void setSnapshotAvailable(Boolean snapshotAvailable) {
        this.snapshotAvailable = snapshotAvailable;
    }

    public Boolean getSaved() {
        return saved;
    }

    public void setSaved(Boolean saved) {
        this.saved = saved;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public List<InventoryShiftSaleItemDTO> getSales() {
        return sales;
    }

    public void setSales(List<InventoryShiftSaleItemDTO> sales) {
        this.sales = sales;
    }

    public List<InventoryShiftReportRowDTO> getRows() {
        return rows;
    }

    public void setRows(List<InventoryShiftReportRowDTO> rows) {
        this.rows = rows;
    }
}
