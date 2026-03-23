package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.DishSetDTO;
import com.shakur.cafehelp.Service.DishSetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dish-sets")
public class DishSetController {
    private final DishSetService dishSetService;

    public DishSetController(DishSetService dishSetService) {
        this.dishSetService = dishSetService;
    }

    @GetMapping
    public ResponseEntity<List<DishSetDTO>> getAll() {
        return ResponseEntity.ok(dishSetService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DishSetDTO> getById(@PathVariable int id) {
        DishSetDTO dto = dishSetService.getById(id);
        if (dto == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<DishSetDTO> create(@RequestBody DishSetDTO dto) {
        return ResponseEntity.ok(dishSetService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DishSetDTO> update(@PathVariable int id, @RequestBody DishSetDTO dto) {
        DishSetDTO updated = dishSetService.update(id, dto);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        boolean deleted = dishSetService.delete(id);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
