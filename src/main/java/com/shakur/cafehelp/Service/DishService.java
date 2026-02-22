package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.DishDTO;
import jooqdata.tables.Dish;
import jooqdata.tables.Order;
import jooqdata.tables.records.DishRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
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

    private Integer nextTechProductId() {
        Integer max = dsl.select(DSL.max(DISH.TECHPRODUCTID))
                .from(DISH)
                .fetchOne(0, Integer.class);
        return (max == null ? 1 : max + 1);
    }

    private Integer normalizeTechProductForCreate(Integer techProduct) {
        return (techProduct == null || techProduct <= 0) ? nextTechProductId() : techProduct;
    }

    // Создание блюда
    public DishDTO createDish(DishDTO dto) {
        DishRecord record = dsl.newRecord(DISH);
        record.setDishname(dto.dishName);
        record.setPrice(dto.price);
        record.setFirstcost(dto.firstCost);
        record.setTechproductid(normalizeTechProductForCreate(dto.techProduct));
        record.setWeight(dto.weight);
        record.setCategory(dto.category);

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
                    dish.setCategory(record.getCategory());
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
        dish.setCategory(record.getCategory());
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
        if (dto.getTechProduct() != null && dto.getTechProduct() > 0) {
            record.setTechproductid(dto.getTechProduct());
        }
        record.setWeight(dto.getWeight());
        record.setCategory(dto.getCategory());

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
