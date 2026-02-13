    package com.shakur.cafehelp.Controller;

    import com.shakur.cafehelp.DTO.TechProductDTO;
    import com.shakur.cafehelp.Service.TechProductService;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.*;

    import java.util.List;

    @RestController
    @RequestMapping("/api/tech-products")
    public class TechProductController {

        private final TechProductService techProductService;

        public TechProductController(TechProductService techProductService) {
            this.techProductService = techProductService;
        }

        // ===============================
        // GET ALL INGREDIENTS BY DISH
        // ===============================
        @GetMapping("/dish/{dishId}")
        public ResponseEntity<List<TechProductDTO>> getByDish(@PathVariable int dishId) {
            List<TechProductDTO> items = techProductService.getByDishId(dishId);
            return ResponseEntity.ok(items);
        }

        // ===============================
        // GET ONE INGREDIENT
        // ===============================
        @GetMapping("/{id}")
        public ResponseEntity<TechProductDTO> getById(@PathVariable int id) {
            TechProductDTO item = techProductService.getById(id);
            if (item == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(item);
        }

        // ===============================
        // CREATE INGREDIENT
        // ===============================
        @PostMapping
        public ResponseEntity<TechProductDTO> create(@RequestBody TechProductDTO dto) {
            TechProductDTO created = techProductService.create(dto);
            return ResponseEntity.ok(created);
        }

        // ===============================
        // UPDATE INGREDIENT
        // ===============================
        @PutMapping("/{id}")
        public ResponseEntity<TechProductDTO> update(@PathVariable int id, @RequestBody TechProductDTO dto) {
            TechProductDTO updated = techProductService.update(id, dto);
            if (updated == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(updated);
        }

        // ===============================
        // DELETE INGREDIENT
        // ===============================
        @DeleteMapping("/{id}")
        public ResponseEntity<Void> delete(@PathVariable int id) {
            boolean deleted = techProductService.delete(id);
            if (!deleted) return ResponseEntity.notFound().build();
            return ResponseEntity.noContent().build();
        }
    }
