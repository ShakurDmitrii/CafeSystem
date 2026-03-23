package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.TechProductDTO;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

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
        validateRecipeRow(techProduct);

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

        validateRecipeRow(techProduct);

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

    private void validateRecipeRow(TechProductDTO dto) {
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

        if (safeWeight(dto.getWeight()) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Вес ингредиента должен быть больше 0");
        }
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
