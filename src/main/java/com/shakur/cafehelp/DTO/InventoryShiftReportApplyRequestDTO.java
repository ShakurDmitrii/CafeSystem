package com.shakur.cafehelp.DTO;

import java.util.List;

public class InventoryShiftReportApplyRequestDTO {
    private Integer shiftId;
    private List<InventoryShiftReportActualRowDTO> rows;

    public Integer getShiftId() {
        return shiftId;
    }

    public void setShiftId(Integer shiftId) {
        this.shiftId = shiftId;
    }

    public List<InventoryShiftReportActualRowDTO> getRows() {
        return rows;
    }

    public void setRows(List<InventoryShiftReportActualRowDTO> rows) {
        this.rows = rows;
    }
}
