package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.DishDTO;
import com.shakur.cafehelp.Service.DishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dishes")
public class DishController {

    private final DishService dishService;

    @Autowired
    public DishController(DishService dishService) {
        this.dishService = dishService;
    }

    // Получить все блюда
    @GetMapping
    public List<DishDTO> getAllDishes() {
        return dishService.getAll();
    }

    // Создать новое блюдо
    @PostMapping
    public DishDTO createDish(@RequestBody DishDTO dishDTO) {
        return dishService.createDish(dishDTO);
    }

    // Получить блюдо по ID
    @GetMapping("/{id}")
    public DishDTO getDishById(@PathVariable int id) {
        return dishService.getById(id);
    }
    // Обновление блюда
    @PutMapping("/{id}")
    public DishDTO updateDish(@PathVariable int id, @RequestBody DishDTO dishDTO) {
    return dishService.updateDish(id, dishDTO);
    }

    // Удаление блюда
    @DeleteMapping("/{id}")
    public boolean deleteDish(@PathVariable int id) {
       return dishService.deleteDish(id);
    }

}
