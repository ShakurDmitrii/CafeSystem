package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.DishDTO;
import org.jooq.Field;
import org.jooq.Record;
import jooqdata.tables.records.DishRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

import static jooqdata.tables.Dish.DISH;

@Service
public class DishService {

    private final DSLContext dsl;
    private static final Field<String> DISH_IMAGE_URL = DSL.field(DSL.name("image_url"), String.class);
    private static final Field<Integer> DISH_CATEGORY_ID = DSL.field(DSL.name("dish", "category_id"), Integer.class);
    private static final org.jooq.Table<?> DISH_CATEGORY = DSL.table(DSL.name("sales", "dish_category")).as("dc");
    private static final Field<Integer> DC_ID = DSL.field(DSL.name("dc", "category_id"), Integer.class);
    private static final Field<String> DC_NAME = DSL.field(DSL.name("dc", "name"), String.class);
    private volatile Boolean imageColumnPresent = null;
    private volatile Boolean categoryIdColumnPresent = null;
    private volatile Boolean categoryTablePresent = null;

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
        Integer categoryId = resolveCategoryId(dto);
        String categoryName = resolveCategoryName(dto, categoryId);
        if (hasImageColumn()) {
            Integer id = dsl.insertInto(DISH)
                    .set(DISH.DISHNAME, dto.dishName)
                    .set(DISH.PRICE, dto.price)
                    .set(DISH.FIRSTCOST, dto.firstCost)
                    .set(DISH.TECHPRODUCTID, normalizeTechProductForCreate(dto.techProduct))
                    .set(DISH.WEIGHT, dto.weight)
                    .set(DISH.CATEGORY, categoryName != null ? categoryName : dto.category)
                    .set(DISH_CATEGORY_ID, hasCategoryIdColumn() ? categoryId : null)
                    .set(DISH_IMAGE_URL, dto.imageUrl)
                    .returning(DISH.DISHID)
                    .fetchOne(DISH.DISHID);
            dto.setDishId(id != null ? id : 0);
            dto.setCategoryId(categoryId);
            dto.setCategoryName(categoryName);
            return dto;
        }

        DishRecord record = dsl.newRecord(DISH);
        record.setDishname(dto.dishName);
        record.setPrice(dto.price);
        record.setFirstcost(dto.firstCost);
        record.setTechproductid(normalizeTechProductForCreate(dto.techProduct));
        record.setWeight(dto.weight);
        record.setCategory(categoryName != null ? categoryName : dto.category);

        record.store();

        dto.setDishId(record.getDishid());
        dto.setCategoryId(categoryId);
        dto.setCategoryName(categoryName);
        dto.setImageUrl(null);
        return dto;
    }

    // Получить все блюда
    public List<DishDTO> getAll() {
        if (hasImageColumn()) {
            if (hasCategoryIdColumn() && hasCategoryTable()) {
                return dsl.select(
                                DISH.DISHID,
                                DISH.DISHNAME,
                                DISH.PRICE,
                                DISH.FIRSTCOST,
                                DISH.TECHPRODUCTID,
                                DISH.WEIGHT,
                                DISH.CATEGORY,
                                DISH_CATEGORY_ID,
                                DC_NAME,
                                DISH_IMAGE_URL
                        )
                        .from(DISH)
                        .leftJoin(DISH_CATEGORY)
                        .on(DISH_CATEGORY_ID.eq(DC_ID))
                        .fetch()
                        .stream()
                        .map(this::toDishDto)
                        .toList();
            }

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
                    dish.setCategoryId(null);
                    dish.setCategoryName(null);
                    dish.setImageUrl(null);
                    return dish;
                }).toList();
    }

    // Получить блюдо по ID
    public DishDTO getById(int id) {
        if (hasImageColumn()) {
            if (hasCategoryIdColumn() && hasCategoryTable()) {
                Record record = dsl.select(
                                DISH.DISHID,
                                DISH.DISHNAME,
                                DISH.PRICE,
                                DISH.FIRSTCOST,
                                DISH.TECHPRODUCTID,
                                DISH.WEIGHT,
                                DISH.CATEGORY,
                                DISH_CATEGORY_ID,
                                DC_NAME,
                                DISH_IMAGE_URL
                        )
                        .from(DISH)
                        .leftJoin(DISH_CATEGORY)
                        .on(DISH_CATEGORY_ID.eq(DC_ID))
                        .where(DISH.DISHID.eq(id))
                        .fetchOne();
                if (record == null) return null;
                return toDishDto(record);
            }

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
        dish.setCategoryId(null);
        dish.setCategoryName(null);
        dish.setImageUrl(null);
        return dish;
    }

    @Transactional
    // Обновление блюда
    public DishDTO updateDish(int id, DishDTO dto) {
        Integer categoryId = resolveCategoryId(dto);
        String categoryName = resolveCategoryName(dto, categoryId);
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
                    .set(DISH.CATEGORY, categoryName != null ? categoryName : dto.getCategory())
                    .set(DISH_CATEGORY_ID, hasCategoryIdColumn() ? categoryId : null)
                    .set(DISH_IMAGE_URL, dto.getImageUrl())
                    .where(DISH.DISHID.eq(id))
                    .execute();
            if (updated == 0) return null;
            dto.setDishId(id);
            dto.setCategoryId(categoryId);
            dto.setCategoryName(categoryName);
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
        record.setCategory(categoryName != null ? categoryName : dto.getCategory());

        record.store();

        dto.setDishId(record.getDishid());
        dto.setCategoryId(categoryId);
        dto.setCategoryName(categoryName);
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

    private boolean hasCategoryIdColumn() {
        if (categoryIdColumnPresent != null) return categoryIdColumnPresent;
        Integer cnt = dsl.selectCount()
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema"), String.class).eq("sales"))
                .and(DSL.field(DSL.name("table_name"), String.class).eq("dish"))
                .and(DSL.field(DSL.name("column_name"), String.class).eq("category_id"))
                .fetchOne(0, Integer.class);
        categoryIdColumnPresent = cnt != null && cnt > 0;
        return categoryIdColumnPresent;
    }

    private boolean hasCategoryTable() {
        if (categoryTablePresent != null) return categoryTablePresent;
        Integer cnt = dsl.selectCount()
                .from(DSL.table(DSL.name("information_schema", "tables")))
                .where(DSL.field(DSL.name("table_schema"), String.class).eq("sales"))
                .and(DSL.field(DSL.name("table_name"), String.class).eq("dish_category"))
                .fetchOne(0, Integer.class);
        categoryTablePresent = cnt != null && cnt > 0;
        return categoryTablePresent;
    }

    private Integer resolveCategoryId(DishDTO dto) {
        Integer id = dto.getCategoryId();
        if (id != null && id > 0) return id;
        String name = firstNonBlank(dto.getCategoryName(), dto.getCategory());
        if (name == null || !hasCategoryTable()) return null;
        return getOrCreateCategoryId(name.trim());
    }

    private String resolveCategoryName(DishDTO dto, Integer categoryId) {
        String name = firstNonBlank(dto.getCategoryName(), dto.getCategory());
        if (name != null) return name.trim();
        if (categoryId != null && hasCategoryTable()) {
            return dsl.select(DC_NAME)
                    .from(DISH_CATEGORY)
                    .where(DC_ID.eq(categoryId))
                    .fetchOne(DC_NAME);
        }
        return null;
    }

    private Integer getOrCreateCategoryId(String name) {
        String normalized = name.trim();
        if (normalized.isEmpty()) return null;
        Integer existing = dsl.select(DC_ID)
                .from(DISH_CATEGORY)
                .where(DSL.lower(DC_NAME).eq(normalized.toLowerCase(Locale.ROOT)))
                .fetchOne(DC_ID);
        if (existing != null) return existing;

        Integer created = dsl.insertInto(DISH_CATEGORY)
                .set(DC_NAME, normalized)
                .onDuplicateKeyIgnore()
                .returning(DC_ID)
                .fetchOne(DC_ID);
        if (created != null) return created;

        return dsl.select(DC_ID)
                .from(DISH_CATEGORY)
                .where(DSL.lower(DC_NAME).eq(normalized.toLowerCase(Locale.ROOT)))
                .fetchOne(DC_ID);
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a;
        if (b != null && !b.trim().isEmpty()) return b;
        return null;
    }

    private DishDTO toDishDto(Record record) {
        DishDTO dish = new DishDTO();
        dish.setDishId(record.get(DISH.DISHID));
        dish.setDishName(record.get(DISH.DISHNAME));
        dish.setPrice(record.get(DISH.PRICE));
        dish.setFirstCost(record.get(DISH.FIRSTCOST));
        dish.setTechProduct(record.get(DISH.TECHPRODUCTID));
        dish.setWeight(record.get(DISH.WEIGHT));
        String categoryName = record.field(DC_NAME) != null ? record.get(DC_NAME) : null;
        Integer categoryId = record.field(DISH_CATEGORY_ID) != null ? record.get(DISH_CATEGORY_ID) : null;
        dish.setCategoryId(categoryId);
        dish.setCategoryName(categoryName);
        dish.setCategory(categoryName != null ? categoryName : record.get(DISH.CATEGORY));
        dish.setImageUrl(record.get(DISH_IMAGE_URL));
        return dish;
    }
}
