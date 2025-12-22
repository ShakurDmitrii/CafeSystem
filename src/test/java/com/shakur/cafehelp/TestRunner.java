package com.shakur.cafehelp;

import com.shakur.cafehelp.Service.MlServices.SalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class TestRunner implements CommandLineRunner {

    private final SalesService salesService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== Testing SalesService ===");

        // Тест 1: Получить продажи
        var sales = salesService.getSalesForML(
                LocalDate.now().minusDays(7),
                LocalDate.now()
        );

        System.out.println("Found " + sales.size() + " sales records");

        if (!sales.isEmpty()) {
            var first = sales.get(0);
            System.out.println("First record:");
            System.out.println("  Roll: " + first.getRollName());
            System.out.println("  Ingredients: " + first.getIngredients());
            System.out.println("  Quantity: " + first.getQuantity());
            System.out.println("  Date: " + first.getSaleDate());
        }

        // Тест 2: Популярные ингредиенты
        var popular = salesService.getPopularIngredients(30, 5);
        System.out.println("\nPopular ingredients: " + popular);
    }
}