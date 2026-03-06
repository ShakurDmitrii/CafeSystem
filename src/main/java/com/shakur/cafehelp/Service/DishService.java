package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.DishDTO;
import jooqdata.tables.Dish;
import org.jooq.Field;
import org.jooq.Record;
import jooqdata.tables.records.DishRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static jooqdata.tables.Dish.DISH;

@Service
public class DishService {

    private final DSLContext dsl;
    private static final Field<String> DISH_IMAGE_URL = DSL.field(DSL.name("image_url"), String.class);
    private volatile Boolean imageColumnPresent = null;

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
        if (hasImageColumn()) {
            Integer id = dsl.insertInto(DISH)
                    .set(DISH.DISHNAME, dto.dishName)
                    .set(DISH.PRICE, dto.price)
                    .set(DISH.FIRSTCOST, dto.firstCost)
                    .set(DISH.TECHPRODUCTID, normalizeTechProductForCreate(dto.techProduct))
                    .set(DISH.WEIGHT, dto.weight)
                    .set(DISH.CATEGORY, dto.category)
                    .set(DISH_IMAGE_URL, dto.imageUrl)
                    .returning(DISH.DISHID)
                    .fetchOne(DISH.DISHID);
            dto.setDishId(id != null ? id : 0);
            return dto;
        }

        DishRecord record = dsl.newRecord(DISH);
        record.setDishname(dto.dishName);
        record.setPrice(dto.price);
        record.setFirstcost(dto.firstCost);
        record.setTechproductid(normalizeTechProductForCreate(dto.techProduct));
        record.setWeight(dto.weight);
        record.setCategory(dto.category);

        record.store();

        dto.setDishId(record.getDishid());
        dto.setImageUrl(null);
        return dto;
    }

    // Получить все блюда
    public List<DishDTO> getAll() {
        if (hasImageColumn()) {
            return dsl.select(
                            DISH.DISHID,
                            DISH.DISHNAME,
                            DISH.PRICE,
                            DISH.FIRSTCOST,
                            DISH.TECHPRODUCTID,
                            DISH.WEIGHT,
                            DISH.CATEGORY,
                            DISH_IMAGE_URL
                    )
                    .from(DISH)
                    .fetch()
                    .stream()
                    .map(this::toDishDto)
                    .toList();
        }

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
                    dish.setImageUrl(null);
                    return dish;
                }).toList();
    }

    // Получить блюдо по ID
    public DishDTO getById(int id) {
        if (hasImageColumn()) {
            Record record = dsl.select(
                            DISH.DISHID,
                            DISH.DISHNAME,
                            DISH.PRICE,
                            DISH.FIRSTCOST,
                            DISH.TECHPRODUCTID,
                            DISH.WEIGHT,
                            DISH.CATEGORY,
                            DISH_IMAGE_URL
                    )
                    .from(DISH)
                    .where(DISH.DISHID.eq(id))
                    .fetchOne();
            if (record == null) return null;
            return toDishDto(record);
        }

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
        dish.setImageUrl(null);
        return dish;
    }

    @Transactional
    // Обновление блюда
    public DishDTO updateDish(int id, DishDTO dto) {
        if (hasImageColumn()) {
            Integer techProductId = dto.getTechProduct();
            if (techProductId == null || techProductId <= 0) {
                techProductId = dsl.select(DISH.TECHPRODUCTID)
                        .from(DISH)
                        .where(DISH.DISHID.eq(id))
                        .fetchOne(DISH.TECHPRODUCTID);
            }

            int updated = dsl.update(DISH)
                    .set(DISH.DISHNAME, dto.getDishName())
                    .set(DISH.PRICE, dto.getPrice())
                    .set(DISH.FIRSTCOST, dto.getFirstCost())
                    .set(DISH.TECHPRODUCTID, techProductId)
                    .set(DISH.WEIGHT, dto.getWeight())
                    .set(DISH.CATEGORY, dto.getCategory())
                    .set(DISH_IMAGE_URL, dto.getImageUrl())
                    .where(DISH.DISHID.eq(id))
                    .execute();
            if (updated == 0) return null;
            dto.setDishId(id);
            return dto;
        }

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
        dto.setImageUrl(null);
        return dto;
    }

    // Удаление блюда
    public boolean deleteDish(int id) {
        int deleted = dsl.deleteFrom(DISH)
                .where(DISH.DISHID.eq(id))
                .execute();
        return deleted > 0;
    }

    private boolean hasImageColumn() {
        if (imageColumnPresent != null) return imageColumnPresent;
        Integer cnt = dsl.selectCount()
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema"), String.class).eq("sales"))
                .and(DSL.field(DSL.name("table_name"), String.class).eq("dish"))
                .and(DSL.field(DSL.name("column_name"), String.class).eq("image_url"))
                .fetchOne(0, Integer.class);
        imageColumnPresent = cnt != null && cnt > 0;
        return imageColumnPresent;
    }

    private DishDTO toDishDto(Record record) {
        DishDTO dish = new DishDTO();
        dish.setDishId(record.get(DISH.DISHID));
        dish.setDishName(record.get(DISH.DISHNAME));
        dish.setPrice(record.get(DISH.PRICE));
        dish.setFirstCost(record.get(DISH.FIRSTCOST));
        dish.setTechProduct(record.get(DISH.TECHPRODUCTID));
        dish.setWeight(record.get(DISH.WEIGHT));
        dish.setCategory(record.get(DISH.CATEGORY));
        dish.setImageUrl(record.get(DISH_IMAGE_URL));
        return dish;
    }
}
