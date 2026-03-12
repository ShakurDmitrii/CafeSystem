package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.DishCategoryDTO;
import com.shakur.cafehelp.Service.DishCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dish-categories")
public class DishCategoryController {

    private final DishCategoryService dishCategoryService;

    public DishCategoryController(DishCategoryService dishCategoryService) {
        this.dishCategoryService = dishCategoryService;
    }

    @GetMapping
    public List<DishCategoryDTO> getAll() {
        return dishCategoryService.getAll();
    }

    @PostMapping
    public DishCategoryDTO create(@RequestBody DishCategoryDTO dto) {
        return dishCategoryService.create(dto);
    }

    @PutMapping("/{id}")
    public DishCategoryDTO update(@PathVariable int id, @RequestBody DishCategoryDTO dto) {
        return dishCategoryService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable int id) {
        return dishCategoryService.delete(id);
    }
}
