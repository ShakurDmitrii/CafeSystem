package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.DishCategoryDTO;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DishCategoryService {

    private final DSLContext dsl;
    private static final Table<?> DISH_CATEGORY = DSL.table(DSL.name("sales", "dish_category"));
    private static final Field<Integer> CATEGORY_ID = DSL.field(DSL.name("category_id"), Integer.class);
    private static final Field<String> CATEGORY_NAME = DSL.field(DSL.name("name"), String.class);

    public DishCategoryService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<DishCategoryDTO> getAll() {
        return dsl.select(CATEGORY_ID, CATEGORY_NAME)
                .from(DISH_CATEGORY)
                .orderBy(CATEGORY_NAME.asc())
                .fetch()
                .map(r -> {
                    DishCategoryDTO dto = new DishCategoryDTO();
                    dto.setCategoryId(r.get(CATEGORY_ID));
                    dto.setName(r.get(CATEGORY_NAME));
                    return dto;
                });
    }

    public DishCategoryDTO create(DishCategoryDTO dto) {
        String name = normalizeName(dto != null ? dto.getName() : null);
        if (name == null) {
            throw new IllegalArgumentException("Название категории обязательно");
        }

        Integer id = dsl.insertInto(DISH_CATEGORY)
                .set(CATEGORY_NAME, name)
                .onDuplicateKeyIgnore()
                .returning(CATEGORY_ID)
                .fetchOne(CATEGORY_ID);
        if (id == null) {
            id = dsl.select(CATEGORY_ID)
                    .from(DISH_CATEGORY)
                    .where(CATEGORY_NAME.eq(name))
                    .fetchOne(CATEGORY_ID);
        }

        DishCategoryDTO result = new DishCategoryDTO();
        result.setCategoryId(id);
        result.setName(name);
        return result;
    }

    public DishCategoryDTO update(int id, DishCategoryDTO dto) {
        String name = normalizeName(dto != null ? dto.getName() : null);
        if (name == null) {
            throw new IllegalArgumentException("Название категории обязательно");
        }

        int updated = dsl.update(DISH_CATEGORY)
                .set(CATEGORY_NAME, name)
                .where(CATEGORY_ID.eq(id))
                .execute();
        if (updated == 0) return null;

        DishCategoryDTO result = new DishCategoryDTO();
        result.setCategoryId(id);
        result.setName(name);
        return result;
    }

    public boolean delete(int id) {
        return dsl.deleteFrom(DISH_CATEGORY)
                .where(CATEGORY_ID.eq(id))
                .execute() > 0;
    }

    private String normalizeName(String raw) {
        if (raw == null) return null;
        String name = raw.trim();
        return name.isEmpty() ? null : name;
    }
}
