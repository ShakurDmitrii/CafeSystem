package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.DishDTO;
import com.shakur.cafehelp.DTO.ShiftDTO;
import com.shakur.cafehelp.Service.ShiftService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @GetMapping("/{id}")
    public ShiftDTO getShift(@PathVariable int id) {
        return shiftService.getShiftById(id);
    }

    @GetMapping
    public List<ShiftDTO> getAllShifts() {
        return shiftService.findAllShifts();
    }

    @PostMapping("/create")
    public ResponseEntity<?> createShift(@RequestBody ShiftDTO shiftDTO) {
        try {
            return ResponseEntity.ok(shiftService.createShift(shiftDTO));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/update")
    public ShiftDTO updateShift(
            @PathVariable int id,
            @RequestBody ShiftDTO shiftDTO) {
        return shiftService.updateShift(id, shiftDTO);
    }

    // ============================
    // Открыть смену
    // ============================
    @PostMapping("/open")
    public ShiftDTO openShift(@RequestParam int personCode) {
        var shiftRecord = shiftService.openShift(personCode);
        ShiftDTO dto = new ShiftDTO();
        dto.shiftId = shiftRecord.getId();
        dto.personCode = shiftRecord.getPersoncode();
        dto.startTime = shiftRecord.getStarttime();
        dto.data = shiftRecord.getData();
        return dto;
    }

    // ============================
    // Закрыть смену
    // ============================
    @PostMapping("/{id}/close")
    public ShiftDTO closeShift(
            @PathVariable int id,
            @RequestParam BigDecimal expenses
    ) {
        var shiftRecord = shiftService.closeShift(id, expenses);
        ShiftDTO dto = new ShiftDTO();
        dto.shiftId = shiftRecord.getId();
        dto.personCode = shiftRecord.getPersoncode();
        dto.startTime = shiftRecord.getStarttime();
        dto.endTime = shiftRecord.getEndtime();
        dto.data = shiftRecord.getData();
        dto.expenses = shiftRecord.getExpenses();
        dto.income = shiftRecord.getIncome();
        dto.profit = shiftRecord.getProfit();
        return dto;
    }
@GetMapping("/getDish/{id}")
    public List<DishDTO> getDish(@PathVariable int id) {
        return shiftService.getDishesByOrderId(id);
}

    @GetMapping("/{id}/z-report")
    public Map<String, Object> getZReport(@PathVariable int id) {
        return shiftService.buildZReport(id);
    }

}
