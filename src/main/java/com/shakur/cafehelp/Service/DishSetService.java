package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.DishSetDTO;
import com.shakur.cafehelp.DTO.DishSetItemDTO;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static jooqdata.tables.Dish.DISH;

@Service
public class DishSetService {
    private static final org.jooq.Table<?> DISH_SET = DSL.table(DSL.name("sales", "dish_set"));
    private static final org.jooq.Table<?> DISH_SET_ITEM = DSL.table(DSL.name("sales", "dish_set_item"));
    private static final org.jooq.Field<Integer> SET_ID = DSL.field(DSL.name("setid"), Integer.class);
    private static final org.jooq.Field<String> SET_NAME = DSL.field(DSL.name("setname"), String.class);
    private static final org.jooq.Field<Double> SET_PRICE = DSL.field(DSL.name("price"), Double.class);
    private static final org.jooq.Field<Double> SET_FIRST_COST = DSL.field(DSL.name("first_cost"), Double.class);
    private static final org.jooq.Field<String> SET_IMAGE_URL = DSL.field(DSL.name("image_url"), String.class);
    private static final org.jooq.Field<Integer> ITEM_ID = DSL.field(DSL.name("set_item_id"), Integer.class);
    private static final org.jooq.Field<Integer> ITEM_SET_ID = DSL.field(DSL.name("set_id"), Integer.class);
    private static final org.jooq.Field<Integer> ITEM_DISH_ID = DSL.field(DSL.name("dish_id"), Integer.class);
    private static final org.jooq.Field<Integer> ITEM_QTY = DSL.field(DSL.name("qty"), Integer.class);
    private static final org.jooq.Table<?> ORDER_DISH = DSL.table(DSL.name("sales", "orderdish"));
    private static final org.jooq.Field<Integer> ORDER_DISH_SET_ID = DSL.field(DSL.name("set_id"), Integer.class);

    private final DSLContext dsl;
    private final RecipeCostService recipeCostService;

    public DishSetService(DSLContext dsl, RecipeCostService recipeCostService) {
        this.dsl = dsl;
        this.recipeCostService = recipeCostService;
    }

    public List<DishSetDTO> getAll() {
        List<DishSetDTO> sets = dsl.select(SET_ID, SET_NAME, SET_PRICE, SET_FIRST_COST, SET_IMAGE_URL)
                .from(DISH_SET)
                .orderBy(SET_NAME.asc())
                .fetch(this::mapSetBase);

        Map<Integer, List<DishSetItemDTO>> itemsBySet = loadItemsBySetIds(
                sets.stream()
                        .map(DishSetDTO::getSetId)
                        .toList()
        );

        sets.forEach(dto -> {
            dto.setItems(itemsBySet.getOrDefault(dto.getSetId(), List.of()));
            dto.setFirstCost(syncAndCalculateCost(dto));
        });

        return sets;
    }

    public DishSetDTO getById(int id) {
        Record record = dsl.select(SET_ID, SET_NAME, SET_PRICE, SET_FIRST_COST, SET_IMAGE_URL)
                .from(DISH_SET)
                .where(SET_ID.eq(id))
                .fetchOne();
        if (record == null) return null;

        DishSetDTO dto = mapSetBase(record);
        dto.setItems(loadItemsBySetIds(List.of(id)).getOrDefault(id, List.of()));
        dto.setFirstCost(syncAndCalculateCost(dto));
        return dto;
    }

    @Transactional
    public DishSetDTO create(DishSetDTO dto) {
        String setName = normalizeName(dto.getSetName());
        double price = normalizePrice(dto.getPrice());
        List<DishSetItemDTO> items = normalizeItems(dto.getItems());

        ensureNameIsUnique(setName, null);

        Integer id = dsl.insertInto(DISH_SET)
                .set(SET_NAME, setName)
                .set(SET_PRICE, price)
                .set(SET_FIRST_COST, 0.0)
                .set(SET_IMAGE_URL, normalizeImageUrl(dto.getImageUrl()))
                .returning(SET_ID)
                .fetchOne(SET_ID);

        persistItems(id, items);
        return getById(id != null ? id : 0);
    }

    @Transactional
    public DishSetDTO update(int id, DishSetDTO dto) {
        Integer existingId = dsl.select(SET_ID)
                .from(DISH_SET)
                .where(SET_ID.eq(id))
                .fetchOne(SET_ID);
        if (existingId == null) return null;

        String setName = normalizeName(dto.getSetName());
        double price = normalizePrice(dto.getPrice());
        List<DishSetItemDTO> items = normalizeItems(dto.getItems());

        ensureNameIsUnique(setName, id);

        dsl.update(DISH_SET)
                .set(SET_NAME, setName)
                .set(SET_PRICE, price)
                .set(SET_IMAGE_URL, normalizeImageUrl(dto.getImageUrl()))
                .where(SET_ID.eq(id))
                .execute();

        dsl.deleteFrom(DISH_SET_ITEM)
                .where(ITEM_SET_ID.eq(id))
                .execute();
        persistItems(id, items);

        return getById(id);
    }

    @Transactional
    public boolean delete(int id) {
        boolean usedByOrder = dsl.fetchExists(
                dsl.selectOne().from(ORDER_DISH).where(ORDER_DISH_SET_ID.eq(id))
        );
        if (usedByOrder) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Набор используется в заказах");
        }
        int deleted = dsl.deleteFrom(DISH_SET)
                .where(SET_ID.eq(id))
                .execute();
        return deleted > 0;
    }

    public boolean hasSetReferencesForDish(int dishId) {
        Integer count = dsl.selectCount()
                .from(DISH_SET_ITEM)
                .where(ITEM_DISH_ID.eq(dishId))
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }

    private DishSetDTO mapSetBase(Record record) {
        DishSetDTO dto = new DishSetDTO();
        dto.setSetId(record.get(SET_ID));
        dto.setSetName(record.get(SET_NAME));
        dto.setPrice(record.get(SET_PRICE));
        dto.setFirstCost(record.get(SET_FIRST_COST));
        dto.setImageUrl(record.get(SET_IMAGE_URL));
        return dto;
    }

    private Map<Integer, List<DishSetItemDTO>> loadItemsBySetIds(List<Integer> setIds) {
        Map<Integer, List<DishSetItemDTO>> result = new LinkedHashMap<>();
        if (setIds == null || setIds.isEmpty()) {
            return result;
        }

        var rows = dsl.select(
                        ITEM_ID,
                        ITEM_SET_ID,
                        ITEM_DISH_ID,
                        ITEM_QTY,
                        DISH.DISHNAME,
                        DISH.PRICE,
                        DISH.FIRSTCOST,
                        DSL.field(DSL.name("image_url"), String.class),
                        DISH.CATEGORY
                )
                .from(DISH_SET_ITEM)
                .join(DISH).on(DISH.DISHID.eq(ITEM_DISH_ID))
                .where(ITEM_SET_ID.in(setIds))
                .orderBy(ITEM_SET_ID.asc(), DISH.DISHNAME.asc())
                .fetch();

        for (Record row : rows) {
            Integer setId = row.get(ITEM_SET_ID);
            if (setId == null) continue;

            DishSetItemDTO item = new DishSetItemDTO();
            item.setSetItemId(row.get(ITEM_ID));
            item.setSetId(setId);
            item.setDishId(row.get(ITEM_DISH_ID));
            item.setQty(row.get(ITEM_QTY));
            item.setDishName(row.get(DISH.DISHNAME));
            item.setDishPrice(row.get(DISH.PRICE));
            item.setDishFirstCost(row.get(DISH.FIRSTCOST));
            item.setImageUrl(row.get(DSL.field(DSL.name("image_url"), String.class)));
            item.setCategoryName(row.get(DISH.CATEGORY));

            result.computeIfAbsent(setId, key -> new ArrayList<>()).add(item);
        }

        return result;
    }

    private double syncAndCalculateCost(DishSetDTO dto) {
        Integer setId = dto.getSetId();
        if (setId == null || setId <= 0) return 0.0;

        Set<Integer> dishIds = new LinkedHashSet<>();
        for (DishSetItemDTO item : dto.getItems()) {
            if (item.getDishId() != null && item.getDishId() > 0) {
                dishIds.add(item.getDishId());
            }
        }

        Map<Integer, Double> costs = recipeCostService.calculateDishCosts(dishIds);
        double total = 0.0;
        for (DishSetItemDTO item : dto.getItems()) {
            int qty = item.getQty() != null ? item.getQty() : 0;
            if (qty <= 0) continue;
            double dishCost = costs.getOrDefault(item.getDishId(), 0.0);
            item.setDishFirstCost(dishCost);
            total += dishCost * qty;
        }

        double rounded = BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP).doubleValue();
        Double stored = dsl.select(SET_FIRST_COST)
                .from(DISH_SET)
                .where(SET_ID.eq(setId))
                .fetchOne(SET_FIRST_COST);

        if (stored == null || Math.abs(stored - rounded) > 0.009) {
            dsl.update(DISH_SET)
                    .set(SET_FIRST_COST, rounded)
                    .where(SET_ID.eq(setId))
                    .execute();
        }

        return rounded;
    }

    private void persistItems(Integer setId, List<DishSetItemDTO> items) {
        if (setId == null || setId <= 0) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить набор");
        }

        for (DishSetItemDTO item : items) {
            dsl.insertInto(DISH_SET_ITEM)
                    .set(ITEM_SET_ID, setId)
                    .set(ITEM_DISH_ID, item.getDishId())
                    .set(ITEM_QTY, item.getQty())
                    .execute();
        }
    }

    private List<DishSetItemDTO> normalizeItems(List<DishSetItemDTO> items) {
        List<DishSetItemDTO> normalized = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Добавьте хотя бы одно блюдо в набор");
        }

        Map<Integer, Integer> qtyByDish = new LinkedHashMap<>();
        for (DishSetItemDTO item : items) {
            if (item == null) continue;
            Integer dishId = item.getDishId();
            int qty = item.getQty() != null ? item.getQty() : 0;
            if (dishId == null || dishId <= 0 || qty <= 0) continue;
            qtyByDish.merge(dishId, qty, Integer::sum);
        }

        if (qtyByDish.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Добавьте хотя бы одно блюдо в набор");
        }

        Set<Integer> existingDishIds = new LinkedHashSet<>(
                dsl.select(DISH.DISHID)
                        .from(DISH)
                        .where(DISH.DISHID.in(qtyByDish.keySet()))
                        .fetch(DISH.DISHID)
        );

        for (Map.Entry<Integer, Integer> entry : qtyByDish.entrySet()) {
            if (!existingDishIds.contains(entry.getKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Одно из выбранных блюд не найдено");
            }
            DishSetItemDTO dto = new DishSetItemDTO();
            dto.setDishId(entry.getKey());
            dto.setQty(entry.getValue());
            normalized.add(dto);
        }

        return normalized;
    }

    private void ensureNameIsUnique(String setName, Integer currentId) {
        if (setName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Название набора обязательно");
        }

        Condition condition = DSL.lower(SET_NAME).eq(setName.toLowerCase(Locale.ROOT));
        if (currentId != null && currentId > 0) {
            condition = condition.and(SET_ID.ne(currentId));
        }

        Integer conflict = dsl.select(SET_ID)
                .from(DISH_SET)
                .where(condition)
                .fetchOne(SET_ID);

        if (conflict != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Набор с таким названием уже существует");
        }
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private double normalizePrice(Double value) {
        double price = value != null ? value : 0.0;
        if (!Double.isFinite(price) || price <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Цена набора должна быть больше 0");
        }
        return BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String normalizeImageUrl(String imageUrl) {
        String normalized = imageUrl == null ? "" : imageUrl.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
