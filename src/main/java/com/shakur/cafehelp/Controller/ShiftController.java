package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.ShiftDTO;
import com.shakur.cafehelp.Service.ShiftService;
import org.springframework.web.bind.annotation.*;

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

}
