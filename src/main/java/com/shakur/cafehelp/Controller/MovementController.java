package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.MovementDTO;
import com.shakur.cafehelp.DTO.MovementReportRowDTO;
import com.shakur.cafehelp.DTO.MovementRequestDTO;
import com.shakur.cafehelp.DTO.MovementTurnoverRowDTO;
import com.shakur.cafehelp.Service.MovementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/movements")
public class MovementController {

    private final MovementService movementService;

    public MovementController(MovementService movementService) {
        this.movementService = movementService;
    }

    @PostMapping
    public ResponseEntity<MovementDTO> createMovement(@RequestBody MovementRequestDTO dto) {
        MovementDTO created = movementService.createMovement(dto);
        if (created == null) return ResponseEntity.unprocessableEntity().build();
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<List<MovementDTO>> getAllMovements() {
        return ResponseEntity.ok(movementService.getAllMovements());
    }

    @PatchMapping("/{id}/date")
    public ResponseEntity<Void> updateMovementDate(
            @PathVariable int id,
            @RequestBody Map<String, String> body
    ) {
        String rawDate = body != null ? body.get("docDate") : null;
        if (rawDate == null || rawDate.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        LocalDateTime parsed;
        try {
            parsed = LocalDateTime.parse(rawDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }

        boolean updated = movementService.updateMovementDate(id, parsed);
        if (!updated) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/report")
    public ResponseEntity<List<MovementReportRowDTO>> getReceiptReport(
            @RequestParam Integer productId,
            @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo
    ) {
        return ResponseEntity.ok(movementService.getReceiptReport(productId, dateFrom, dateTo));
    }

    @GetMapping("/turnover-report")
    public ResponseEntity<List<MovementTurnoverRowDTO>> getTurnoverReport(
            @RequestParam(required = false) Integer productId,
            @RequestParam(required = false) String productName,
            @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo
    ) {
        return ResponseEntity.ok(movementService.getTurnoverReport(productId, productName, dateFrom, dateTo));
    }
}
