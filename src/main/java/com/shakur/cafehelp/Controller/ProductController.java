package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.ProductDTO;
import com.shakur.cafehelp.Service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")

public class ProductController {
    private final ProductService productService;
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductDTO> getAllProducts() {
        return productService.getProducts();
    }

    @GetMapping("/{supplierId}")
    public List<ProductDTO> getProducts(@PathVariable int supplierId) {
        return productService.getAllSupplierProducts(supplierId);
    }
@GetMapping("/{id}")
public ProductDTO getProduct(@PathVariable int id) {
        return productService.getProductById(id);
}
    @GetMapping("/favorite/{supplierId}")
    public List<ProductDTO> getFavoriteProducts(@PathVariable int supplierId) {
        return productService.getAllFavoriteSupplierProduct(supplierId);
    }
    @PostMapping
    public ProductDTO addProduct(@RequestBody ProductDTO productDTO) {
        return productService.createProduct(productDTO);
    }

}
