package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.ProductWarehouseDTO;
import com.shakur.cafehelp.DTO.WareHouseDTO;
import com.shakur.cafehelp.Service.WareHouseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/warehouses")
public class WareHouseController {

    private final WareHouseService wareHouseService;

    public WareHouseController(WareHouseService wareHouseService) {
        this.wareHouseService = wareHouseService;
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

    // Можно еще добавить GET /warehouses/{id}/products для просмотра продуктов на складе
    @GetMapping("/{id}/products")
    public ResponseEntity<List<ProductWarehouseDTO>> getProductsOnWarehouse(@PathVariable("id") int warehouseId) {
        List<ProductWarehouseDTO> products = wareHouseService.getProductsOnWarehouse(warehouseId);
        return ResponseEntity.ok(products);
    }

}
