package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.DishDTO;
import jooqdata.tables.Dish;
import jooqdata.tables.Order;
import jooqdata.tables.records.DishRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static jooqdata.tables.Dish.DISH;

@Service
public class DishService {

    private final DSLContext dsl;

    public DishService(DSLContext dsl) {
        this.dsl = dsl;
    }

    // Создание блюда
    public DishDTO createDish(DishDTO dto) {
        DishRecord record = dsl.newRecord(DISH);
        record.setDishname(dto.dishName);
        record.setPrice(dto.price);
        record.setFirstcost(dto.firstCost);
        record.setTechproductid(dto.techProduct);
        record.setWeight(dto.weight);

        record.store();

        dto.setDishId(record.getDishid());
        return dto;
    }

    // Получить все блюда
    public List<DishDTO> getAll() {
        return dsl.selectFrom(DISH)
                .fetch()
                .stream()
                .map(record -> {
                    DishDTO dish = new DishDTO();
                    dish.setDishId(record.getDishid());
                    dish.setDishName(record.getDishname());
                    dish.setPrice(record.getPrice());
                    dish.setFirstCost(record.getFirstcost());
                    dish.setTechProduct(record.getTechproductid());
                    dish.setWeight(record.getWeight());
                    return dish;
                }).toList();
    }

    // Получить блюдо по ID
    public DishDTO getById(int id) {
        DishRecord record = dsl.selectFrom(DISH)
                .where(DISH.DISHID.eq(id))
                .fetchOne();

        if (record == null) return null;

        DishDTO dish = new DishDTO();
        dish.setDishId(record.getDishid());
        dish.setDishName(record.getDishname());
        dish.setPrice(record.getPrice());
        dish.setFirstCost(record.getFirstcost());
        dish.setTechProduct(record.getTechproductid());
        dish.setWeight(record.getWeight());
        return dish;
    }

    @Transactional
    // Обновление блюда
    public DishDTO updateDish(int id, DishDTO dto) {
        DishRecord record = dsl.selectFrom(DISH)
                .where(DISH.DISHID.eq(id))
                .fetchOne();

        if (record == null) return null;

        record.setDishname(dto.getDishName());
        record.setPrice(dto.getPrice());
        record.setFirstcost(dto.getFirstCost());
        record.setTechproductid(dto.getTechProduct());
        record.setWeight(dto.getWeight());

        record.store();

        dto.setDishId(record.getDishid());
        return dto;
    }

    // Удаление блюда
    public boolean deleteDish(int id) {
        int deleted = dsl.deleteFrom(DISH)
                .where(DISH.DISHID.eq(id))
                .execute();
        return deleted > 0;
    }
}
