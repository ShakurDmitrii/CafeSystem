package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.DishDTO;
import com.shakur.cafehelp.DTO.ShiftDTO;
import com.shakur.cafehelp.Service.ShiftService;
import com.shakur.cafehelp.exception.InvalidShiftRequestException;
import com.shakur.cafehelp.exception.ShiftNotFoundException;
import com.shakur.cafehelp.exception.ShiftStateConflictException;
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
        return shiftService.getShiftById(shiftRecord.getId());
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
        return shiftService.getShiftById(shiftRecord.getId());
    }
@GetMapping("/getDish/{id}")
    public List<DishDTO> getDish(@PathVariable int id) {
        return shiftService.getDishesByOrderId(id);
}

    @GetMapping("/{id}/z-report")
    public Map<String, Object> getZReport(@PathVariable int id) {
        return shiftService.buildZReport(id);
    }

    @ExceptionHandler(InvalidShiftRequestException.class)
    public ResponseEntity<Map<String, String>> invalidShift(InvalidShiftRequestException exception) {
        return shiftError(HttpStatus.BAD_REQUEST, "INVALID_SHIFT", exception.getMessage());
    }

    @ExceptionHandler(ShiftNotFoundException.class)
    public ResponseEntity<Map<String, String>> shiftNotFound(ShiftNotFoundException exception) {
        return shiftError(HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ShiftStateConflictException.class)
    public ResponseEntity<Map<String, String>> shiftConflict(ShiftStateConflictException exception) {
        return shiftError(HttpStatus.CONFLICT, "SHIFT_STATE_CONFLICT", exception.getMessage());
    }

    private ResponseEntity<Map<String, String>> shiftError(
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity.status(status).body(Map.of(
                "code", code,
                "message", message != null ? message : status.getReasonPhrase()
        ));
    }

}
