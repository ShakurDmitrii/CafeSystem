    package com.shakur.cafehelp.Controller;

    import com.shakur.cafehelp.DTO.ConsProductDTO;
    import com.shakur.cafehelp.Service.ConsProductService;
    import org.springframework.web.bind.annotation.*;

    import java.util.List;

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
        public ConsProductDTO addConsProduct(@RequestBody ConsProductDTO consProductDTO) {
            return consProductService.createConsProduct(consProductDTO);
        }

        @DeleteMapping("/{id}")
        public ConsProductDTO deleteConsProduct(@PathVariable int id) {
            return consProductService.deleteConsProduct(id);
        }

    }
