package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.PreparationDTO;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Locale;

@Service
public class PreparationService {
    private final DSLContext dsl;
    private final RecipeCostService recipeCostService;

    public PreparationService(DSLContext dsl, RecipeCostService recipeCostService) {
        this.dsl = dsl;
        this.recipeCostService = recipeCostService;
    }

    public List<PreparationDTO> getAll() {
        List<PreparationDTO> items = dsl.select(
                        RecipeSchema.PREPARATION_ID,
                        RecipeSchema.PREPARATION_NAME,
                        RecipeSchema.PREPARATION_OUTPUT_WEIGHT
                )
                .from(RecipeSchema.PREPARATION)
                .orderBy(RecipeSchema.PREPARATION_NAME.asc())
                .fetch(this::toDto);

        var costsByPreparation = recipeCostService.calculatePreparationCosts(
                items.stream()
                        .map(PreparationDTO::getPreparationId)
                        .toList()
        );

        items.forEach(item -> item.setCost(costsByPreparation.getOrDefault(item.getPreparationId(), 0.0)));
        return items;
    }

    public PreparationDTO getById(int id) {
        Record record = dsl.select(
                        RecipeSchema.PREPARATION_ID,
                        RecipeSchema.PREPARATION_NAME,
                        RecipeSchema.PREPARATION_OUTPUT_WEIGHT
                )
                .from(RecipeSchema.PREPARATION)
                .where(RecipeSchema.PREPARATION_ID.eq(id))
                .fetchOne();
        if (record == null) return null;

        PreparationDTO dto = toDto(record);
        dto.setCost(recipeCostService.calculatePreparationCost(id));
        return dto;
    }

    @Transactional
    public PreparationDTO create(PreparationDTO dto) {
        String name = normalizeName(dto.getPreparationName());
        double outputWeight = normalizeOutputWeight(dto.getOutputWeight());
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Название заготовки обязательно");
        }

        Integer existingId = dsl.select(RecipeSchema.PREPARATION_ID)
                .from(RecipeSchema.PREPARATION)
                .where(DSL.lower(RecipeSchema.PREPARATION_NAME).eq(name.toLowerCase(Locale.ROOT)))
                .fetchOne(RecipeSchema.PREPARATION_ID);
        if (existingId != null) {
            return getById(existingId);
        }

        Integer id = dsl.insertInto(RecipeSchema.PREPARATION)
                .set(RecipeSchema.PREPARATION_NAME, name)
                .set(RecipeSchema.PREPARATION_OUTPUT_WEIGHT, outputWeight)
                .returning(RecipeSchema.PREPARATION_ID)
                .fetchOne(RecipeSchema.PREPARATION_ID);

        dto.setPreparationId(id);
        dto.setPreparationName(name);
        dto.setOutputWeight(outputWeight);
        dto.setCost(0.0);
        return dto;
    }

    @Transactional
    public PreparationDTO update(int id, PreparationDTO dto) {
        Record existing = dsl.select(
                        RecipeSchema.PREPARATION_ID,
                        RecipeSchema.PREPARATION_NAME,
                        RecipeSchema.PREPARATION_OUTPUT_WEIGHT
                )
                .from(RecipeSchema.PREPARATION)
                .where(RecipeSchema.PREPARATION_ID.eq(id))
                .fetchOne();
        if (existing == null) return null;

        String name = dto.getPreparationName() != null
                ? normalizeName(dto.getPreparationName())
                : existing.get(RecipeSchema.PREPARATION_NAME);
        double outputWeight = dto.getOutputWeight() != null
                ? normalizeOutputWeight(dto.getOutputWeight())
                : safeWeight(existing.get(RecipeSchema.PREPARATION_OUTPUT_WEIGHT));

        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Название заготовки обязательно");
        }

        Integer conflictingId = dsl.select(RecipeSchema.PREPARATION_ID)
                .from(RecipeSchema.PREPARATION)
                .where(DSL.lower(RecipeSchema.PREPARATION_NAME).eq(name.toLowerCase(Locale.ROOT)))
                .and(RecipeSchema.PREPARATION_ID.ne(id))
                .fetchOne(RecipeSchema.PREPARATION_ID);
        if (conflictingId != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заготовка с таким названием уже существует");
        }

        dsl.update(RecipeSchema.PREPARATION)
                .set(RecipeSchema.PREPARATION_NAME, name)
                .set(RecipeSchema.PREPARATION_OUTPUT_WEIGHT, outputWeight)
                .where(RecipeSchema.PREPARATION_ID.eq(id))
                .execute();

        PreparationDTO updated = new PreparationDTO();
        updated.setPreparationId(id);
        updated.setPreparationName(name);
        updated.setOutputWeight(outputWeight);
        updated.setCost(recipeCostService.calculatePreparationCost(id));
        recipeCostService.refreshAffectedDishCosts(null, id);
        return updated;
    }

    @Transactional
    public boolean delete(int id) {
        boolean exists = dsl.fetchExists(
                dsl.selectOne().from(RecipeSchema.PREPARATION)
                        .where(RecipeSchema.PREPARATION_ID.eq(id))
        );
        if (!exists) return false;

        boolean usedAsIngredient = dsl.fetchExists(
                dsl.selectOne().from(RecipeSchema.TECHPRODUCT)
                        .where(RecipeSchema.TECH_INGREDIENT_PREPARATION_ID.eq(id))
        );
        boolean hasWarehouseRows = dsl.fetchExists(
                dsl.selectOne().from(DSL.table(DSL.name("sales", "preparationwarehouse")))
                        .where(DSL.field(DSL.name("preparationid"), Integer.class).eq(id))
        );
        if (usedAsIngredient || hasWarehouseRows) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Заготовка используется в другой рецептуре или складском учёте"
            );
        }

        int deleted = dsl.deleteFrom(RecipeSchema.PREPARATION)
                .where(RecipeSchema.PREPARATION_ID.eq(id))
                .execute();
        return deleted > 0;
    }

    private PreparationDTO toDto(Record record) {
        PreparationDTO dto = new PreparationDTO();
        dto.setPreparationId(record.get(RecipeSchema.PREPARATION_ID));
        dto.setPreparationName(record.get(RecipeSchema.PREPARATION_NAME));
        dto.setOutputWeight(record.get(RecipeSchema.PREPARATION_OUTPUT_WEIGHT));
        return dto;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private double normalizeOutputWeight(Double value) {
        double normalized = safeWeight(value);
        if (!Double.isFinite(normalized) || normalized <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выход заготовки должен быть больше 0");
        }
        return normalized;
    }

    private double safeWeight(Double value) {
        return value != null ? value : 0.0;
    }
}
