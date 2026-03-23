package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.PreparationDTO;
import com.shakur.cafehelp.DTO.PreparationProductionRequestDTO;
import com.shakur.cafehelp.DTO.PreparationProductionResponseDTO;
import com.shakur.cafehelp.DTO.PreparationWarehouseDTO;
import com.shakur.cafehelp.Service.PreparationProductionService;
import com.shakur.cafehelp.Service.PreparationService;
import com.shakur.cafehelp.Service.WareHouseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/preparations")
public class PreparationController {
    private final PreparationService preparationService;
    private final PreparationProductionService preparationProductionService;
    private final WareHouseService wareHouseService;

    public PreparationController(
            PreparationService preparationService,
            PreparationProductionService preparationProductionService,
            WareHouseService wareHouseService
    ) {
        this.preparationService = preparationService;
        this.preparationProductionService = preparationProductionService;
        this.wareHouseService = wareHouseService;
    }

    @GetMapping
    public ResponseEntity<List<PreparationDTO>> getAll() {
        return ResponseEntity.ok(preparationService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PreparationDTO> getById(@PathVariable int id) {
        PreparationDTO dto = preparationService.getById(id);
        if (dto == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<PreparationDTO> create(@RequestBody PreparationDTO dto) {
        return ResponseEntity.ok(preparationService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PreparationDTO> update(@PathVariable int id, @RequestBody PreparationDTO dto) {
        PreparationDTO updated = preparationService.update(id, dto);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/stock")
    public ResponseEntity<List<PreparationWarehouseDTO>> getStock(@PathVariable int id) {
        List<PreparationWarehouseDTO> stock = wareHouseService.getAll()
                .stream()
                .map(warehouse -> wareHouseService.getPreparationsOnWarehouse(warehouse.getWarehouseId()))
                .flatMap(List::stream)
                .filter(item -> item.getPreparationId() != null && item.getPreparationId() == id)
                .toList();
        return ResponseEntity.ok(stock);
    }

    @PostMapping("/{id}/produce")
    public ResponseEntity<PreparationProductionResponseDTO> produce(
            @PathVariable int id,
            @RequestBody PreparationProductionRequestDTO request
    ) {
        return ResponseEntity.ok(preparationProductionService.produce(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        boolean deleted = preparationService.delete(id);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
