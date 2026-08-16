package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.TechProductDTO;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TechProductService {
    private final DSLContext dsl;
    private final RecipeCostService recipeCostService;

    public TechProductService(DSLContext dsl, RecipeCostService recipeCostService) {
        this.dsl = dsl;
        this.recipeCostService = recipeCostService;
    }

    @Transactional
    public TechProductDTO create(TechProductDTO techProduct) {
        lockRecipeGraph();
        validateRecipeRow(techProduct, null);

        Integer id = dsl.insertInto(RecipeSchema.TECHPRODUCT)
                .set(RecipeSchema.TECH_DISH_ID, techProduct.getDishId())
                .set(RecipeSchema.TECH_PREPARATION_ID, techProduct.getPreparationId())
                .set(RecipeSchema.TECH_PRODUCT_ID, techProduct.getProductId())
                .set(RecipeSchema.TECH_INGREDIENT_PREPARATION_ID, techProduct.getIngredientPreparationId())
                .set(RecipeSchema.TECH_WEIGHT, safeWeight(techProduct.getWeight()))
                .set(RecipeSchema.TECH_WASTE, safeWaste(techProduct.getWaste()))
                .returning(RecipeSchema.TECHPRODUCT_ID)
                .fetchOne(RecipeSchema.TECHPRODUCT_ID);

        techProduct.setTechProductId(id);
        techProduct.setWeight(safeWeight(techProduct.getWeight()));
        techProduct.setWaste(safeWaste(techProduct.getWaste()));
        recipeCostService.refreshAffectedDishCosts(techProduct.getDishId(), techProduct.getPreparationId());
        return techProduct;
    }

    public TechProductDTO getById(int id) {
        Record record = dsl.select(
                        RecipeSchema.TECHPRODUCT_ID,
                        RecipeSchema.TECH_DISH_ID,
                        RecipeSchema.TECH_PREPARATION_ID,
                        RecipeSchema.TECH_PRODUCT_ID,
                        RecipeSchema.TECH_INGREDIENT_PREPARATION_ID,
                        RecipeSchema.TECH_WEIGHT,
                        RecipeSchema.TECH_WASTE
                )
                .from(RecipeSchema.TECHPRODUCT)
                .where(RecipeSchema.TECHPRODUCT_ID.eq(id))
                .fetchOne();
        return record == null ? null : recordToDTO(record);
    }

    public List<TechProductDTO> getByDishId(int dishId) {
        return dsl.select(
                        RecipeSchema.TECHPRODUCT_ID,
                        RecipeSchema.TECH_DISH_ID,
                        RecipeSchema.TECH_PREPARATION_ID,
                        RecipeSchema.TECH_PRODUCT_ID,
                        RecipeSchema.TECH_INGREDIENT_PREPARATION_ID,
                        RecipeSchema.TECH_WEIGHT,
                        RecipeSchema.TECH_WASTE
                )
                .from(RecipeSchema.TECHPRODUCT)
                .where(RecipeSchema.TECH_DISH_ID.eq(dishId))
                .orderBy(RecipeSchema.TECHPRODUCT_ID.asc())
                .fetch(this::recordToDTO);
    }

    public List<TechProductDTO> getByPreparationId(int preparationId) {
        return dsl.select(
                        RecipeSchema.TECHPRODUCT_ID,
                        RecipeSchema.TECH_DISH_ID,
                        RecipeSchema.TECH_PREPARATION_ID,
                        RecipeSchema.TECH_PRODUCT_ID,
                        RecipeSchema.TECH_INGREDIENT_PREPARATION_ID,
                        RecipeSchema.TECH_WEIGHT,
                        RecipeSchema.TECH_WASTE
                )
                .from(RecipeSchema.TECHPRODUCT)
                .where(RecipeSchema.TECH_PREPARATION_ID.eq(preparationId))
                .orderBy(RecipeSchema.TECHPRODUCT_ID.asc())
                .fetch(this::recordToDTO);
    }

    @Transactional
    public TechProductDTO update(int id, TechProductDTO techProduct) {
        lockRecipeGraph();
        TechProductDTO existing = getById(id);
        if (existing == null) return null;
        OwnerRef previousOwner = OwnerRef.of(existing);

        if (techProduct.getDishId() == null && techProduct.getPreparationId() == null) {
            techProduct.setDishId(existing.getDishId());
            techProduct.setPreparationId(existing.getPreparationId());
        }
        if (techProduct.getProductId() == null && techProduct.getIngredientPreparationId() == null) {
            techProduct.setProductId(existing.getProductId());
            techProduct.setIngredientPreparationId(existing.getIngredientPreparationId());
        }
        if (techProduct.getWeight() == null) {
            techProduct.setWeight(existing.getWeight());
        }
        if (techProduct.getWaste() == null) {
            techProduct.setWaste(existing.getWaste());
        }

        validateRecipeRow(techProduct, id);

        dsl.update(RecipeSchema.TECHPRODUCT)
                .set(RecipeSchema.TECH_DISH_ID, techProduct.getDishId())
                .set(RecipeSchema.TECH_PREPARATION_ID, techProduct.getPreparationId())
                .set(RecipeSchema.TECH_PRODUCT_ID, techProduct.getProductId())
                .set(RecipeSchema.TECH_INGREDIENT_PREPARATION_ID, techProduct.getIngredientPreparationId())
                .set(RecipeSchema.TECH_WEIGHT, safeWeight(techProduct.getWeight()))
                .set(RecipeSchema.TECH_WASTE, safeWaste(techProduct.getWaste()))
                .where(RecipeSchema.TECHPRODUCT_ID.eq(id))
                .execute();

        TechProductDTO updated = getById(id);
        refreshOwners(previousOwner, OwnerRef.of(updated));
        return updated;
    }

    @Transactional
    public boolean delete(int id) {
        TechProductDTO existing = getById(id);
        if (existing == null) return false;

        int deleted = dsl.deleteFrom(RecipeSchema.TECHPRODUCT)
                .where(RecipeSchema.TECHPRODUCT_ID.eq(id))
                .execute();
        if (deleted > 0) {
            recipeCostService.refreshAffectedDishCosts(existing.getDishId(), existing.getPreparationId());
        }
        return deleted > 0;
    }

    private void refreshOwners(OwnerRef... owners) {
        if (owners == null) return;
        for (OwnerRef owner : owners) {
            if (owner == null) continue;
            recipeCostService.refreshAffectedDishCosts(owner.dishId(), owner.preparationId());
        }
    }

    private TechProductDTO recordToDTO(Record record) {
        TechProductDTO dto = new TechProductDTO();
        dto.setTechProductId(record.get(RecipeSchema.TECHPRODUCT_ID));
        dto.setDishId(record.get(RecipeSchema.TECH_DISH_ID));
        dto.setPreparationId(record.get(RecipeSchema.TECH_PREPARATION_ID));
        dto.setProductId(record.get(RecipeSchema.TECH_PRODUCT_ID));
        dto.setIngredientPreparationId(record.get(RecipeSchema.TECH_INGREDIENT_PREPARATION_ID));
        dto.setWeight(record.get(RecipeSchema.TECH_WEIGHT));
        dto.setWaste(record.get(RecipeSchema.TECH_WASTE));
        return dto;
    }

    private void validateRecipeRow(TechProductDTO dto, Integer currentRowId) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Строка техкарты обязательна");
        }

        int ownerCount = 0;
        if (dto.getDishId() != null) ownerCount++;
        if (dto.getPreparationId() != null) ownerCount++;
        if (ownerCount != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Строка техкарты должна принадлежать либо блюду, либо заготовке"
            );
        }

        int ingredientCount = 0;
        if (dto.getProductId() != null) ingredientCount++;
        if (dto.getIngredientPreparationId() != null) ingredientCount++;
        if (ingredientCount != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Строка техкарты должна ссылаться либо на продукт, либо на заготовку"
            );
        }

        if (dto.getPreparationId() != null
                && dto.getIngredientPreparationId() != null
                && dto.getPreparationId().equals(dto.getIngredientPreparationId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Заготовка не может содержать саму себя напрямую"
            );
        }

        double weight = safeWeight(dto.getWeight());
        if (!Double.isFinite(weight) || weight <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Вес ингредиента должен быть больше 0");
        }

        double waste = safeWaste(dto.getWaste());
        if (!Double.isFinite(waste) || waste < 0 || waste > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Процент отхода должен быть от 0 до 100");
        }

        ensureReferencesExist(dto);
        ensureNoPreparationCycle(dto, currentRowId);
    }

    private void ensureReferencesExist(TechProductDTO dto) {
        if (dto.getDishId() != null && !dsl.fetchExists(
                dsl.selectOne().from(jooqdata.tables.Dish.DISH)
                        .where(jooqdata.tables.Dish.DISH.DISHID.eq(dto.getDishId()))
        )) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Блюдо для техкарты не найдено");
        }
        if (dto.getPreparationId() != null && !preparationExists(dto.getPreparationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заготовка-владелец не найдена");
        }
        if (dto.getProductId() != null && !dsl.fetchExists(
                dsl.selectOne().from(jooqdata.tables.Product.PRODUCT)
                        .where(jooqdata.tables.Product.PRODUCT.PRODUCTID.eq(dto.getProductId()))
        )) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Продукт для техкарты не найден");
        }
        if (dto.getIngredientPreparationId() != null && !preparationExists(dto.getIngredientPreparationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заготовка-ингредиент не найдена");
        }
    }

    private boolean preparationExists(int id) {
        return dsl.fetchExists(
                dsl.selectOne().from(RecipeSchema.PREPARATION)
                        .where(RecipeSchema.PREPARATION_ID.eq(id))
        );
    }

    private void ensureNoPreparationCycle(TechProductDTO dto, Integer currentRowId) {
        Integer ownerId = dto.getPreparationId();
        Integer ingredientId = dto.getIngredientPreparationId();
        if (ownerId == null || ingredientId == null) return;

        Map<Integer, List<Integer>> graph = new HashMap<>();
        var query = dsl.select(
                        RecipeSchema.TECHPRODUCT_ID,
                        RecipeSchema.TECH_PREPARATION_ID,
                        RecipeSchema.TECH_INGREDIENT_PREPARATION_ID
                )
                .from(RecipeSchema.TECHPRODUCT)
                .where(RecipeSchema.TECH_PREPARATION_ID.isNotNull())
                .and(RecipeSchema.TECH_INGREDIENT_PREPARATION_ID.isNotNull());

        for (Record row : query.fetch()) {
            Integer rowId = row.get(RecipeSchema.TECHPRODUCT_ID);
            if (currentRowId != null && currentRowId.equals(rowId)) continue;
            Integer from = row.get(RecipeSchema.TECH_PREPARATION_ID);
            Integer to = row.get(RecipeSchema.TECH_INGREDIENT_PREPARATION_ID);
            graph.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
        }
        graph.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(ingredientId);

        ArrayDeque<Integer> pending = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();
        pending.add(ingredientId);
        while (!pending.isEmpty()) {
            Integer current = pending.removeFirst();
            if (ownerId.equals(current)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Обнаружена циклическая рецептура заготовок"
                );
            }
            if (!visited.add(current)) continue;
            pending.addAll(graph.getOrDefault(current, List.of()));
        }
    }

    private void lockRecipeGraph() {
        dsl.fetch("select pg_advisory_xact_lock(?)", 7_210_041L);
    }

    private double safeWeight(Double value) {
        return value != null ? value : 0.0;
    }

    private double safeWaste(Double value) {
        return value != null ? value : 0.0;
    }

    private record OwnerRef(Integer dishId, Integer preparationId) {
        static OwnerRef of(TechProductDTO dto) {
            if (dto == null) return null;
            return new OwnerRef(dto.getDishId(), dto.getPreparationId());
        }
    }
}
