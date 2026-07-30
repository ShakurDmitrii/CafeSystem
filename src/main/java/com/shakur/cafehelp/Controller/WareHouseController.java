package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.PreparationWarehouseDTO;
import com.shakur.cafehelp.DTO.InventoryShiftReportApplyRequestDTO;
import com.shakur.cafehelp.DTO.InventoryShiftReportDTO;
import com.shakur.cafehelp.DTO.ProductWarehouseDTO;
import com.shakur.cafehelp.DTO.WareHouseDTO;
import com.shakur.cafehelp.Service.InventoryShiftReportService;
import com.shakur.cafehelp.Service.WareHouseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/warehouses")
public class WareHouseController {

    private final WareHouseService wareHouseService;
    private final InventoryShiftReportService inventoryShiftReportService;

    public WareHouseController(
            WareHouseService wareHouseService,
            InventoryShiftReportService inventoryShiftReportService
    ) {
        this.wareHouseService = wareHouseService;
        this.inventoryShiftReportService = inventoryShiftReportService;
    }

    // Создание склада
    @PostMapping
    public ResponseEntity<WareHouseDTO> createWarehouse(@RequestBody WareHouseDTO dto) {
        WareHouseDTO created = wareHouseService.createWareHouse(dto);
        return ResponseEntity.ok(created);
    }

    // Получить все склады
    @GetMapping
    public ResponseEntity<List<WareHouseDTO>> getAllWarehouses() {
        List<WareHouseDTO> list = wareHouseService.getAll();
        return ResponseEntity.ok(list);
    }

    // Получить склад по ID
    @GetMapping("/{id}")
    public ResponseEntity<WareHouseDTO> getWarehouseById(@PathVariable int id) {
        WareHouseDTO wh = wareHouseService.getById(id);
        if (wh == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(wh);
    }

    // Обновление склада
    @PutMapping("/{id}")
    public ResponseEntity<WareHouseDTO> updateWarehouse(@PathVariable int id, @RequestBody WareHouseDTO dto) {
        WareHouseDTO updated = wareHouseService.updateWareHouse(id, dto);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/main")
    public ResponseEntity<Void> setMainWarehouse(@PathVariable int id) {
        boolean ok = wareHouseService.setMainWarehouse(id);
        if (!ok) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().build();
    }

    // Удаление склада
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWarehouse(@PathVariable int id) {
        boolean deleted = wareHouseService.deleteWareHouse(id);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().build();
    }

    // --- новый метод добавления продуктов ---
    @PostMapping("/{id}/products")
    public ResponseEntity<Void> addProductsToWarehouse(
            @PathVariable("id") int warehouseId,
            @RequestBody List<ProductWarehouseDTO> products
    ) {
        wareHouseService.addProductsToWarehouse(warehouseId, products);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/products")
    public ResponseEntity<List<ProductWarehouseDTO>> getProductsOnWarehouse(@PathVariable("id") int warehouseId) {
        List<ProductWarehouseDTO> products = wareHouseService.getProductsOnWarehouse(warehouseId);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}/preparations")
    public ResponseEntity<List<PreparationWarehouseDTO>> getPreparationsOnWarehouse(@PathVariable("id") int warehouseId) {
        return ResponseEntity.ok(wareHouseService.getPreparationsOnWarehouse(warehouseId));
    }

    @GetMapping("/{id}/inventory-shift-report")
    public ResponseEntity<?> getInventoryShiftReport(
            @PathVariable("id") int warehouseId,
            @RequestParam(value = "shiftId", required = false) Integer shiftId
    ) {
        try {
            InventoryShiftReportDTO report = inventoryShiftReportService.getReport(warehouseId, shiftId);
            return ResponseEntity.ok(report);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/inventory-shift-report/apply")
    public ResponseEntity<?> applyInventoryShiftReport(
            @PathVariable("id") int warehouseId,
            @RequestBody InventoryShiftReportApplyRequestDTO request
    ) {
        try {
            InventoryShiftReportDTO report = inventoryShiftReportService.applyActualBalances(warehouseId, request);
            return ResponseEntity.ok(report);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    /** Добавить или списать количество продукта на складе (body: { "delta": число }) */
    @PatchMapping("/{warehouseId}/products/{productId}/quantity")
    public ResponseEntity<Void> adjustProductQuantity(
            @PathVariable("warehouseId") int warehouseId,
            @PathVariable("productId") int productId,
            @RequestBody java.util.Map<String, Number> body
    ) {
        Number deltaNum = body != null ? body.get("delta") : null;
        if (deltaNum == null) return ResponseEntity.badRequest().build();
        double delta = deltaNum.doubleValue();
        boolean ok = wareHouseService.adjustQuantity(warehouseId, productId, delta);
        if (!ok) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{warehouseId}/preparations/{preparationId}/quantity")
    public ResponseEntity<Void> adjustPreparationQuantity(
            @PathVariable("warehouseId") int warehouseId,
            @PathVariable("preparationId") int preparationId,
            @RequestBody java.util.Map<String, Number> body
    ) {
        Number deltaNum = body != null ? body.get("delta") : null;
        if (deltaNum == null) return ResponseEntity.badRequest().build();
        boolean ok = wareHouseService.adjustPreparationQuantity(warehouseId, preparationId, deltaNum.doubleValue());
        if (!ok) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok().build();
    }

}
