    package com.shakur.cafehelp.Controller;

    import com.shakur.cafehelp.DTO.ConsProductDTO;
    import com.shakur.cafehelp.Service.ConsProductService;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.*;

    import java.util.List;
    import java.util.Map;

    @RestController
    @RequestMapping("/api/consProduct")
    @CrossOrigin(origins = "http://localhost:3000")

    public class ConsProductController {
        private final ConsProductService consProductService;
        public ConsProductController(ConsProductService consProductService) {
            this.consProductService = consProductService;
        }

        @GetMapping
        public List<ConsProductDTO> getConsProduct() {
            return consProductService.getConsProduct();
        }

        @GetMapping("/product/{id}") ConsProductDTO getConsProductById(@PathVariable int id) {
            return consProductService.getConsProductById(id);
        }

        @GetMapping("/{consignmentId}")
        public List<ConsProductDTO> getConsProductByConsignment(@PathVariable int consignmentId) {
            List<ConsProductDTO> products = consProductService.getConsProductByConsId(consignmentId);
            return products != null ? products : List.of(); // никогда не возвращаем null
        }


        @PostMapping
        public ResponseEntity<?> addConsProduct(@RequestBody ConsProductDTO consProductDTO) {
            try {
                return ResponseEntity.ok(consProductService.createConsProduct(consProductDTO));
            } catch (IllegalArgumentException | IllegalStateException e) {
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<?> deleteConsProduct(@PathVariable int id) {
            try {
                return ResponseEntity.ok(consProductService.deleteConsProduct(id));
            } catch (IllegalArgumentException | IllegalStateException e) {
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }

    }
