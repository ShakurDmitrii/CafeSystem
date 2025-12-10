package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.FavoriteProductDTO;
import com.shakur.cafehelp.Service.FavoriteProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favoriteProducts")

public class FavoriteProductController {

    private FavoriteProductService favoriteProductService;
    public FavoriteProductController(FavoriteProductService favoriteProductService) {
        this.favoriteProductService = favoriteProductService;
    }
    @GetMapping("/{id}")
    public FavoriteProductDTO getFavoriteProduct(@PathVariable int id) {
        return  this.favoriteProductService.findById(id);
    }

    @GetMapping
    public List<FavoriteProductDTO> getFavoriteProducts() {
        return this.favoriteProductService.findAll();
    }

    @PostMapping
    public FavoriteProductDTO addFavoriteProduct(@RequestBody FavoriteProductDTO favoriteProductDTO) {
        return favoriteProductService.createFavoriteProduct(favoriteProductDTO);
    }
}
