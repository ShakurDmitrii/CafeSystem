package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.DishDTO;
import com.shakur.cafehelp.DTO.ShiftDTO;
import com.shakur.cafehelp.Service.ShiftService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

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
    public ShiftDTO createShift(@RequestBody ShiftDTO shiftDTO) {
        return shiftService.createShift(shiftDTO);
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

}
