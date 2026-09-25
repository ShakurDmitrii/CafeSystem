package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ProductDTO;
import com.shakur.cafehelp.DTO.ConsumableRuleDTO;
import jooqdata.tables.Product;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductService {
    private DSLContext dsl;
    private static final Field<String> PRODUCT_UNIT = DSL.field(DSL.name("unit"), String.class);
    private static final Field<String> PRODUCT_BASE_UNIT = DSL.field(DSL.name("base_unit"), String.class);
    private static final Field<BigDecimal> PRODUCT_UNIT_FACTOR = DSL.field(DSL.name("unit_factor"), BigDecimal.class);
    private static final Field<String> PRODUCT_IMAGE_URL = DSL.field(DSL.name("image_url"), String.class);
    private static final Field<String> PRODUCT_ITEM_TYPE = DSL.field(DSL.name("item_type"), String.class);
    private static final org.jooq.Table<?> PRODUCT_SUPPLIER = DSL.table(DSL.name("sales", "product_supplier"));
    private static final Field<Integer> PS_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<Integer> PS_SUPPLIER_ID = DSL.field(DSL.name("supplier_id"), Integer.class);
    private static final Field<BigDecimal> PS_DEFAULT_PRICE = DSL.field(DSL.name("default_price"), BigDecimal.class);
    private static final Field<String> PS_PURCHASE_UNIT = DSL.field(DSL.name("purchase_unit"), String.class);
    private static final Field<BigDecimal> PS_PURCHASE_UNIT_FACTOR = DSL.field(DSL.name("purchase_unit_factor"), BigDecimal.class);
    private static final Field<String> PS_SUPPLIER_SKU = DSL.field(DSL.name("supplier_sku"), String.class);
    private volatile Boolean unitColumnsPresent = null;
    private volatile Boolean imageColumnPresent = null;
    private static final org.jooq.Table<?> PRODUCT_WAREHOUSE = DSL.table(DSL.name("sales", "productwarehouse"));
    private static final Field<Integer> PW_PRODUCT_ID = DSL.field(DSL.name("productid"), Integer.class);
    private static final Field<Integer> PW_WAREHOUSE_ID = DSL.field(DSL.name("warehouseid"), Integer.class);
    private static final Field<Double> PW_QUANTITY = DSL.field(DSL.name("quantity"), Double.class);
    private static final Field<BigDecimal> PW_INVENTORY_VALUE = DSL.field(DSL.name("inventory_value"), BigDecimal.class);

    private final ConsumableService consumableService;

    public ProductService(DSLContext dsl, ConsumableService consumableService){
        this.dsl = dsl;
        this.consumableService = consumableService;
    }


    public List<ProductDTO> getProducts() {
        if (!hasUnitColumns()) {
            List<ProductDTO> result = dsl.selectFrom(Product.PRODUCT)
                    .fetch()
                    .stream()
                    .map(record -> {
                        ProductDTO dto = new ProductDTO();
                        dto.productName = record.getProductname();
                        dto.productId = record.getProductid();
                        dto.productPrice = record.getProductprice();
                        dto.isFavorite = record.getIsfavourite();
                        dto.waste = record.getWaste();
                        dto.supplierId = record.getSupplierid();
                        dto.unit = "g";
                        dto.baseUnit = "g";
                        dto.unitFactor = BigDecimal.ONE;
                        dto.imageUrl = null;
                        return dto;
                    }).toList();
            return enrichWithAverageStockPrice(result);
        }
        if (!hasImageColumn()) {
            List<ProductDTO> result = dsl.select(
                            Product.PRODUCT.PRODUCTID,
                            Product.PRODUCT.SUPPLIERID,
                            Product.PRODUCT.PRODUCTNAME,
                            Product.PRODUCT.PRODUCTPRICE,
                            Product.PRODUCT.WASTE,
                            Product.PRODUCT.ISFAVOURITE,
                            PRODUCT_UNIT,
                            PRODUCT_BASE_UNIT,
                            PRODUCT_UNIT_FACTOR
                    )
                    .from(Product.PRODUCT)
                    .fetch()
                    .stream()
                    .map(this::toDto)
                    .toList();
            return enrichWithAverageStockPrice(result);
        }
        List<ProductDTO> result = dsl.select(
                        Product.PRODUCT.PRODUCTID,
                        Product.PRODUCT.SUPPLIERID,
                        Product.PRODUCT.PRODUCTNAME,
                        Product.PRODUCT.PRODUCTPRICE,
                        Product.PRODUCT.WASTE,
                        Product.PRODUCT.ISFAVOURITE,
                        PRODUCT_UNIT,
                        PRODUCT_BASE_UNIT,
                        PRODUCT_UNIT_FACTOR,
                        PRODUCT_IMAGE_URL
                )
                .from(Product.PRODUCT)
                .fetch()
                .stream()
                .map(this::toDto)
                .toList();
        return enrichWithAverageStockPrice(result);
    }
    public List<ProductDTO> getAllFavoriteSupplierProduct(int supplierId){
        if (!hasUnitColumns()) {
            List<ProductDTO> result = dsl.select(Product.PRODUCT.fields())
                    .from(Product.PRODUCT)
                    .join(PRODUCT_SUPPLIER).on(PS_PRODUCT_ID.eq(Product.PRODUCT.PRODUCTID))
                    .where(PS_SUPPLIER_ID.eq(supplierId))
                    .and(Product.PRODUCT.ISFAVOURITE.eq(true))
                    .fetch()
                    .stream()
                    .map(record -> {
                        ProductDTO dto = new ProductDTO();
                        dto.productId = record.get(Product.PRODUCT.PRODUCTID);
                        dto.productName = record.get(Product.PRODUCT.PRODUCTNAME);
                        dto.productPrice = record.get(Product.PRODUCT.PRODUCTPRICE);
                        dto.supplierId = record.get(Product.PRODUCT.SUPPLIERID);
                        dto.waste = record.get(Product.PRODUCT.WASTE);
                        dto.isFavorite = record.get(Product.PRODUCT.ISFAVOURITE);
                        dto.unit = "g";
                        dto.baseUnit = "g";
                        dto.unitFactor = BigDecimal.ONE;
                        dto.imageUrl = null;
                        return dto;
                    }).toList();
            return enrichForSupplier(result, supplierId);
        }
        if (!hasImageColumn()) {
            List<ProductDTO> result = dsl.select(
                            Product.PRODUCT.PRODUCTID,
                            Product.PRODUCT.SUPPLIERID,
                            Product.PRODUCT.PRODUCTNAME,
                            Product.PRODUCT.PRODUCTPRICE,
                            Product.PRODUCT.WASTE,
                            Product.PRODUCT.ISFAVOURITE,
                            PRODUCT_UNIT,
                            PRODUCT_BASE_UNIT,
                            PRODUCT_UNIT_FACTOR
                    )
                    .from(Product.PRODUCT)
                    .join(PRODUCT_SUPPLIER).on(PS_PRODUCT_ID.eq(Product.PRODUCT.PRODUCTID))
                    .where(PS_SUPPLIER_ID.eq(supplierId))
                    .and(Product.PRODUCT.ISFAVOURITE.eq(true))
                    .fetch()
                    .stream()
                    .map(this::toDto)
                    .toList();
            return enrichForSupplier(result, supplierId);
        }
        List<ProductDTO> result = dsl.select(
                        Product.PRODUCT.PRODUCTID,
                        Product.PRODUCT.SUPPLIERID,
                        Product.PRODUCT.PRODUCTNAME,
                        Product.PRODUCT.PRODUCTPRICE,
                        Product.PRODUCT.WASTE,
                        Product.PRODUCT.ISFAVOURITE,
                        PRODUCT_UNIT,
                        PRODUCT_BASE_UNIT,
                        PRODUCT_UNIT_FACTOR,
                        PRODUCT_IMAGE_URL
                )
                .from(Product.PRODUCT)
                .join(PRODUCT_SUPPLIER).on(PS_PRODUCT_ID.eq(Product.PRODUCT.PRODUCTID))
                .where(PS_SUPPLIER_ID.eq(supplierId))
                .and(Product.PRODUCT.ISFAVOURITE.eq(true))
                .fetch()
                .stream()
                .map(this::toDto)
                .toList();
        return enrichForSupplier(result, supplierId);

    }
    public List<ProductDTO> getAllSupplierProducts(int id){
        if (!hasUnitColumns()) {
            List<ProductDTO> result = dsl.select(Product.PRODUCT.fields())
                    .from(Product.PRODUCT)
                    .join(PRODUCT_SUPPLIER).on(PS_PRODUCT_ID.eq(Product.PRODUCT.PRODUCTID))
                    .where(PS_SUPPLIER_ID.eq(id))
                    .fetch()
                    .stream()
                    .map(record -> {
                        ProductDTO dto = new ProductDTO();
                        dto.productId = record.get(Product.PRODUCT.PRODUCTID);
                        dto.productName = record.get(Product.PRODUCT.PRODUCTNAME);
                        dto.productPrice = record.get(Product.PRODUCT.PRODUCTPRICE);
                        dto.supplierId = record.get(Product.PRODUCT.SUPPLIERID);
                        dto.waste = record.get(Product.PRODUCT.WASTE);
                        dto.isFavorite = record.get(Product.PRODUCT.ISFAVOURITE);
                        dto.unit = "g";
                        dto.baseUnit = "g";
                        dto.unitFactor = BigDecimal.ONE;
                        dto.imageUrl = null;
                        return dto;
                    }).toList();
            return enrichForSupplier(result, id);
        }
        if (!hasImageColumn()) {
            List<ProductDTO> result = dsl.select(
                            Product.PRODUCT.PRODUCTID,
                            Product.PRODUCT.SUPPLIERID,
                            Product.PRODUCT.PRODUCTNAME,
                            Product.PRODUCT.PRODUCTPRICE,
                            Product.PRODUCT.WASTE,
                            Product.PRODUCT.ISFAVOURITE,
                            PRODUCT_UNIT,
                            PRODUCT_BASE_UNIT,
                            PRODUCT_UNIT_FACTOR
                    )
                    .from(Product.PRODUCT)
                    .join(PRODUCT_SUPPLIER).on(PS_PRODUCT_ID.eq(Product.PRODUCT.PRODUCTID))
                    .where(PS_SUPPLIER_ID.eq(id))
                    .fetch()
                    .stream()
                    .map(this::toDto)
                    .toList();
            return enrichForSupplier(result, id);
        }
        List<ProductDTO> result = dsl.select(
                        Product.PRODUCT.PRODUCTID,
                        Product.PRODUCT.SUPPLIERID,
                        Product.PRODUCT.PRODUCTNAME,
                        Product.PRODUCT.PRODUCTPRICE,
                        Product.PRODUCT.WASTE,
                        Product.PRODUCT.ISFAVOURITE,
                        PRODUCT_UNIT,
                        PRODUCT_BASE_UNIT,
                        PRODUCT_UNIT_FACTOR,
                        PRODUCT_IMAGE_URL
                )
                .from(Product.PRODUCT)
                .join(PRODUCT_SUPPLIER).on(PS_PRODUCT_ID.eq(Product.PRODUCT.PRODUCTID))
                .where(PS_SUPPLIER_ID.eq(id))
                .fetch()
                .stream()
                .map(this::toDto)
                .toList();
        return enrichForSupplier(result, id);
    }
    public ProductDTO getProductById(int id) {
        if (!hasUnitColumns()) {
            ProductDTO dto = dsl.selectFrom(Product.PRODUCT)
                    .where(Product.PRODUCT.PRODUCTID.eq(id))
                    .fetchOptional()
                    .map(record -> {
                        ProductDTO mappedDto = new ProductDTO();
                        mappedDto.productId = record.getProductid();
                        mappedDto.productName = record.getProductname();
                        mappedDto.productPrice = record.getProductprice();
                        mappedDto.supplierId = record.getSupplierid();
                        mappedDto.waste = record.getWaste();
                        mappedDto.isFavorite = record.getIsfavourite();
                        mappedDto.unit = "g";
                        mappedDto.baseUnit = "g";
                        mappedDto.unitFactor = BigDecimal.ONE;
                        mappedDto.imageUrl = null;
                        return mappedDto;
                    }).orElseThrow();
            dto.setAverageStockPrice(loadAverageStockPriceMap().get(dto.getProductId()));
            enrichClassification(dto);
            return dto;
        }
        if (!hasImageColumn()) {
            ProductDTO dto = dsl.select(
                            Product.PRODUCT.PRODUCTID,
                            Product.PRODUCT.SUPPLIERID,
                            Product.PRODUCT.PRODUCTNAME,
                            Product.PRODUCT.PRODUCTPRICE,
                            Product.PRODUCT.WASTE,
                            Product.PRODUCT.ISFAVOURITE,
                            PRODUCT_UNIT,
                            PRODUCT_BASE_UNIT,
                            PRODUCT_UNIT_FACTOR
                    )
                    .from(Product.PRODUCT)
                    .where(Product.PRODUCT.PRODUCTID.eq(id))
                    .fetchOptional()
                    .map(this::toDto).orElseThrow();
            dto.setAverageStockPrice(loadAverageStockPriceMap().get(dto.getProductId()));
            enrichClassification(dto);
            return dto;
        }
        ProductDTO dto = dsl.select(
                        Product.PRODUCT.PRODUCTID,
                        Product.PRODUCT.SUPPLIERID,
                        Product.PRODUCT.PRODUCTNAME,
                        Product.PRODUCT.PRODUCTPRICE,
                        Product.PRODUCT.WASTE,
                        Product.PRODUCT.ISFAVOURITE,
                        PRODUCT_UNIT,
                        PRODUCT_BASE_UNIT,
                        PRODUCT_UNIT_FACTOR,
                        PRODUCT_IMAGE_URL
                )
                .from(Product.PRODUCT)
                .where(Product.PRODUCT.PRODUCTID.eq(id))
                .fetchOptional()
                .map(this::toDto).orElseThrow();
        dto.setAverageStockPrice(loadAverageStockPriceMap().get(dto.getProductId()));
        enrichClassification(dto);
        return dto;
    }
    @Transactional
    public ProductDTO createProduct(ProductDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Данные продукта обязательны");
        }
        String unit = dto.unit != null && !dto.unit.isBlank() ? dto.unit.trim().toLowerCase() : "g";
        String baseUnit = dto.baseUnit != null && !dto.baseUnit.isBlank()
                ? dto.baseUnit.trim().toLowerCase()
                : defaultBaseUnit(unit);
        BigDecimal unitFactor = dto.unitFactor != null ? dto.unitFactor : BigDecimal.ONE;

        Integer supplierId = dto.supplierId != null && dto.supplierId > 0 ? dto.supplierId : null;
        String normalizedName = dto.productName != null ? dto.productName.trim() : "";
        BigDecimal productPrice = dto.productPrice;
        Double waste = dto.waste != null ? dto.waste : 0.0;
        validateProduct(normalizedName, productPrice, waste, unit, baseUnit, unitFactor, supplierId);
        if (!normalizedName.isEmpty()) {
            Integer existingId = dsl.select(Product.PRODUCT.PRODUCTID)
                    .from(Product.PRODUCT)
                    .where(DSL.lower(DSL.trim(Product.PRODUCT.PRODUCTNAME)).eq(normalizedName.toLowerCase()))
                    .limit(1)
                    .fetchOne(Product.PRODUCT.PRODUCTID);
            if (existingId != null) {
                if (supplierId != null) {
                    linkProductToSupplier(existingId, supplierId);
                    updateSupplierOffer(existingId, supplierId, dto, false);
                }
                if (dto.itemType != null) {
                    applyConsumableConfiguration(existingId, dto);
                }
                ProductDTO existing = getProductById(existingId);
                if (supplierId != null) {
                    existing.supplierId = supplierId;
                }
                return existing;
            }
        }

        Integer id;
        if (hasUnitColumns()) {
            var insert = dsl.insertInto(Product.PRODUCT)
                    .set(Product.PRODUCT.SUPPLIERID, supplierId)
                    .set(Product.PRODUCT.PRODUCTNAME, normalizedName.isEmpty() ? dto.productName : normalizedName)
                    .set(Product.PRODUCT.PRODUCTPRICE, productPrice)
                    .set(Product.PRODUCT.WASTE, waste)
                    .set(Product.PRODUCT.ISFAVOURITE, Boolean.TRUE.equals(dto.isFavorite))
                    .set(PRODUCT_UNIT, unit)
                    .set(PRODUCT_BASE_UNIT, baseUnit)
                    .set(PRODUCT_UNIT_FACTOR, unitFactor);
            if (hasImageColumn()) {
                insert.set(PRODUCT_IMAGE_URL, dto.imageUrl);
            }
            id = insert.returning(Product.PRODUCT.PRODUCTID)
                    .fetchOne(Product.PRODUCT.PRODUCTID);
        } else {
            id = dsl.insertInto(Product.PRODUCT)
                    .set(Product.PRODUCT.SUPPLIERID, supplierId)
                    .set(Product.PRODUCT.PRODUCTNAME, normalizedName.isEmpty() ? dto.productName : normalizedName)
                    .set(Product.PRODUCT.PRODUCTPRICE, productPrice)
                    .set(Product.PRODUCT.WASTE, waste)
                    .set(Product.PRODUCT.ISFAVOURITE, Boolean.TRUE.equals(dto.isFavorite))
                    .returning(Product.PRODUCT.PRODUCTID)
                    .fetchOne(Product.PRODUCT.PRODUCTID);
        }

        dto.productId = id != null ? id : 0;
        dto.unit = unit;
        dto.baseUnit = baseUnit;
        dto.unitFactor = unitFactor;
        dto.productName = normalizedName;
        dto.productPrice = productPrice;
        dto.waste = waste;
        dto.isFavorite = Boolean.TRUE.equals(dto.isFavorite);
        dto.itemType = dto.itemType != null ? dto.itemType : "ingredient";
        if (!hasImageColumn()) dto.imageUrl = null;
        if (supplierId != null && dto.productId != 0) {
            linkProductToSupplier(dto.productId, supplierId);
            updateSupplierOffer(dto.productId, supplierId, dto, true);
        }
        if (dto.productId != 0 && dto.itemType != null) {
            applyConsumableConfiguration(dto.productId, dto);
        }
        return dto;
    }

    @Transactional
    public ProductDTO updateProduct(int id, ProductDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Данные продукта обязательны");
        }
        Field<String> unitField = hasUnitColumns() ? PRODUCT_UNIT : DSL.inline("g").as("unit");
        Field<String> baseUnitField = hasUnitColumns() ? PRODUCT_BASE_UNIT : DSL.inline("g").as("base_unit");
        Field<BigDecimal> unitFactorField = hasUnitColumns() ? PRODUCT_UNIT_FACTOR : DSL.inline(BigDecimal.ONE).as("unit_factor");
        Field<String> imageField = hasImageColumn() ? PRODUCT_IMAGE_URL : DSL.inline((String) null).as("image_url");

        Record existingRecord = dsl.select(
                        Product.PRODUCT.PRODUCTID,
                        Product.PRODUCT.SUPPLIERID,
                        Product.PRODUCT.PRODUCTNAME,
                        Product.PRODUCT.PRODUCTPRICE,
                        Product.PRODUCT.WASTE,
                        Product.PRODUCT.ISFAVOURITE,
                        unitField,
                        baseUnitField,
                        unitFactorField,
                        imageField
                )
                .from(Product.PRODUCT)
                .where(Product.PRODUCT.PRODUCTID.eq(id))
                .fetchOne();

        if (existingRecord == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Продукт не найден");
        }

        Integer supplierId = dto.supplierId != null && dto.supplierId > 0
                ? dto.supplierId
                : existingRecord.get(Product.PRODUCT.SUPPLIERID);
        String productName = dto.productName != null
                ? dto.productName.trim()
                : existingRecord.get(Product.PRODUCT.PRODUCTNAME);
        BigDecimal productPrice = dto.productPrice != null
                ? dto.productPrice
                : existingRecord.get(Product.PRODUCT.PRODUCTPRICE);
        Double waste = dto.waste != null
                ? dto.waste
                : existingRecord.get(Product.PRODUCT.WASTE);
        Boolean isFavorite = dto.isFavorite != null
                ? dto.isFavorite
                : existingRecord.get(Product.PRODUCT.ISFAVOURITE);
        String unit = dto.unit != null
                ? dto.unit.trim().toLowerCase()
                : existingRecord.get(unitField);
        String baseUnit = dto.baseUnit != null
                ? dto.baseUnit.trim().toLowerCase()
                : existingRecord.get(baseUnitField);
        BigDecimal unitFactor = dto.unitFactor != null ? dto.unitFactor : existingRecord.get(unitFactorField);
        String imageUrl = dto.imageUrl != null
                ? dto.imageUrl
                : existingRecord.get(imageField);

        validateProduct(productName, productPrice, waste, unit, baseUnit, unitFactor, supplierId);

        String existingUnit = existingRecord.get(unitField);
        String existingBaseUnit = existingRecord.get(baseUnitField);
        BigDecimal existingFactor = existingRecord.get(unitFactorField) != null
                ? existingRecord.get(unitFactorField)
                : BigDecimal.ONE;
        boolean unitDefinitionChanged = !java.util.Objects.equals(unit, existingUnit)
                || !java.util.Objects.equals(baseUnit, existingBaseUnit)
                || unitFactor.compareTo(existingFactor) != 0;
        if (unitDefinitionChanged) {
            Double stockQuantity = dsl.select(DSL.sum(PW_QUANTITY))
                    .from(PRODUCT_WAREHOUSE)
                    .where(PW_PRODUCT_ID.eq(id))
                    .fetchOne(0, Double.class);
            if (stockQuantity != null && stockQuantity > 0.000001d) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Единицы продукта нельзя менять при наличии остатка. Измените единицу предложения поставщика"
                );
            }
        }

        Integer duplicateId = dsl.select(Product.PRODUCT.PRODUCTID)
                .from(Product.PRODUCT)
                .where(DSL.lower(DSL.trim(Product.PRODUCT.PRODUCTNAME)).eq(productName.trim().toLowerCase()))
                .and(Product.PRODUCT.PRODUCTID.ne(id))
                .limit(1)
                .fetchOne(Product.PRODUCT.PRODUCTID);
        if (duplicateId != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ингредиент с таким названием уже существует. Добавьте поставщика в существующую карточку"
            );
        }

        var update = dsl.update(Product.PRODUCT)
                .set(Product.PRODUCT.SUPPLIERID, supplierId)
                .set(Product.PRODUCT.PRODUCTNAME, productName)
                .set(Product.PRODUCT.PRODUCTPRICE, productPrice)
                .set(Product.PRODUCT.WASTE, waste)
                .set(Product.PRODUCT.ISFAVOURITE, isFavorite);

        if (hasUnitColumns()) {
            update.set(PRODUCT_UNIT, unit)
                    .set(PRODUCT_BASE_UNIT, baseUnit)
                    .set(PRODUCT_UNIT_FACTOR, unitFactor);
        }
        if (hasImageColumn()) {
            update.set(PRODUCT_IMAGE_URL, imageUrl);
        }

        update.where(Product.PRODUCT.PRODUCTID.eq(id)).execute();

        if (supplierId != null) {
            linkProductToSupplier(id, supplierId);
            updateSupplierOffer(id, supplierId, dto, false);
        }
        if (dto.itemType != null) {
            applyConsumableConfiguration(id, dto);
        }

        ProductDTO updated = getProductById(id);
        if (supplierId != null) {
            updated.supplierId = supplierId;
        }
        return updated;
    }

    private void linkProductToSupplier(int productId, int supplierId) {
        dsl.insertInto(PRODUCT_SUPPLIER)
                .columns(PS_PRODUCT_ID, PS_SUPPLIER_ID)
                .values(productId, supplierId)
                .onConflict(PS_PRODUCT_ID, PS_SUPPLIER_ID)
                .doNothing()
                .execute();
    }

    private void applyConsumableConfiguration(int productId, ProductDTO product) {
        ConsumableRuleDTO rule = new ConsumableRuleDTO();
        rule.setItemType(product.itemType);
        rule.setBasis(product.consumableBasis);
        rule.setDefaultQuantity(product.consumableDefaultQuantity);
        rule.setTriggerQuantity(product.consumableTriggerQuantity);
        rule.setDishCategoryId(product.consumableDishCategoryId);
        rule.setActive(product.consumableActive);
        consumableService.configure(productId, rule);
    }

    private void updateSupplierOffer(int productId, int supplierId, ProductDTO dto, boolean useProductDefaults) {
        BigDecimal price = dto.supplierPrice != null
                ? dto.supplierPrice
                : (useProductDefaults ? dto.productPrice : null);
        String unit = dto.supplierUnit != null && !dto.supplierUnit.isBlank()
                ? dto.supplierUnit.trim().toLowerCase()
                : (useProductDefaults ? dto.unit : null);
        BigDecimal factor = dto.supplierUnitFactor != null
                ? dto.supplierUnitFactor
                : (useProductDefaults ? dto.unitFactor : null);
        if (price != null && price.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Цена поставщика не может быть отрицательной");
        }
        if (factor != null && factor.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Коэффициент единицы поставщика должен быть больше нуля");
        }

        var update = dsl.update(PRODUCT_SUPPLIER).set(PS_PRODUCT_ID, PS_PRODUCT_ID);
        boolean changed = false;
        if (price != null) {
            update.set(PS_DEFAULT_PRICE, price);
            changed = true;
        }
        if (unit != null) {
            update.set(PS_PURCHASE_UNIT, unit);
            changed = true;
        }
        if (factor != null) {
            update.set(PS_PURCHASE_UNIT_FACTOR, factor);
            changed = true;
        }
        if (dto.supplierSku != null) {
            update.set(PS_SUPPLIER_SKU, dto.supplierSku.trim().isEmpty() ? null : dto.supplierSku.trim());
            changed = true;
        }
        if (changed) {
            update.where(PS_PRODUCT_ID.eq(productId))
                    .and(PS_SUPPLIER_ID.eq(supplierId))
                    .execute();
        }
    }

    private void validateProduct(
            String name,
            BigDecimal price,
            Double waste,
            String unit,
            String baseUnit,
            BigDecimal unitFactor,
            Integer supplierId
    ) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Название продукта обязательно");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Цена продукта не может быть отрицательной");
        }
        if (waste == null || !Double.isFinite(waste) || waste < 0 || waste > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Процент отхода должен быть от 0 до 100");
        }
        Set<String> units = Set.of("g", "kg", "ml", "l", "pcs");
        Set<String> baseUnits = Set.of("g", "ml", "pcs");
        if (!units.contains(unit) || !baseUnits.contains(baseUnit) || !defaultBaseUnit(unit).equals(baseUnit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Несовместимая пара единиц измерения");
        }
        if (unitFactor == null || unitFactor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Коэффициент единиц должен быть больше 0");
        }
        if (supplierId != null && !dsl.fetchExists(
                dsl.selectOne().from(DSL.table(DSL.name("sales", "supplier")))
                        .where(DSL.field(DSL.name("supplierid"), Integer.class).eq(supplierId))
        )) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Поставщик не найден");
        }
    }

    private String defaultBaseUnit(String unit) {
        if ("g".equals(unit) || "kg".equals(unit)) return "g";
        if ("ml".equals(unit) || "l".equals(unit)) return "ml";
        if ("pcs".equals(unit)) return "pcs";
        return "";
    }

    private boolean hasUnitColumns() {
        if (unitColumnsPresent != null) return unitColumnsPresent;
        Integer cnt = dsl.selectCount()
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema"), String.class).eq("sales"))
                .and(DSL.field(DSL.name("table_name"), String.class).eq("product"))
                .and(DSL.field(DSL.name("column_name"), String.class).in("unit", "base_unit", "unit_factor"))
                .fetchOne(0, Integer.class);
        unitColumnsPresent = cnt != null && cnt >= 3;
        return unitColumnsPresent;
    }

    private boolean hasImageColumn() {
        if (imageColumnPresent != null) return imageColumnPresent;
        Integer cnt = dsl.selectCount()
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema"), String.class).eq("sales"))
                .and(DSL.field(DSL.name("table_name"), String.class).eq("product"))
                .and(DSL.field(DSL.name("column_name"), String.class).eq("image_url"))
                .fetchOne(0, Integer.class);
        imageColumnPresent = cnt != null && cnt > 0;
        return imageColumnPresent;
    }

    private ProductDTO toDto(Record record) {
        ProductDTO dto = new ProductDTO();
        dto.productId = record.get(Product.PRODUCT.PRODUCTID);
        dto.productName = record.get(Product.PRODUCT.PRODUCTNAME);
        dto.productPrice = record.get(Product.PRODUCT.PRODUCTPRICE);
        dto.isFavorite = record.get(Product.PRODUCT.ISFAVOURITE);
        dto.waste = record.get(Product.PRODUCT.WASTE);
        dto.supplierId = record.get(Product.PRODUCT.SUPPLIERID);
        dto.unit = record.get(PRODUCT_UNIT) != null ? record.get(PRODUCT_UNIT) : "g";
        dto.baseUnit = record.get(PRODUCT_BASE_UNIT) != null ? record.get(PRODUCT_BASE_UNIT) : dto.unit;
        dto.unitFactor = record.get(PRODUCT_UNIT_FACTOR) != null ? record.get(PRODUCT_UNIT_FACTOR) : BigDecimal.ONE;
        dto.imageUrl = hasImageColumn() ? record.get(PRODUCT_IMAGE_URL) : null;
        return dto;
    }

    private List<ProductDTO> enrichWithAverageStockPrice(List<ProductDTO> products) {
        Map<Integer, BigDecimal> avgByProduct = loadAverageStockPriceMap();
        Map<Integer, String> typeByProduct = products.isEmpty()
                ? Map.of()
                : dsl.select(Product.PRODUCT.PRODUCTID, PRODUCT_ITEM_TYPE)
                        .from(Product.PRODUCT)
                        .where(Product.PRODUCT.PRODUCTID.in(products.stream().map(ProductDTO::getProductId).toList()))
                        .fetchMap(Product.PRODUCT.PRODUCTID, PRODUCT_ITEM_TYPE);
        for (ProductDTO dto : products) {
            dto.setAverageStockPrice(avgByProduct.get(dto.getProductId()));
            dto.setItemType(typeByProduct.getOrDefault(dto.getProductId(), "ingredient"));
        }
        return products;
    }

    private void enrichClassification(ProductDTO dto) {
        String itemType = dsl.select(PRODUCT_ITEM_TYPE)
                .from(Product.PRODUCT)
                .where(Product.PRODUCT.PRODUCTID.eq(dto.getProductId()))
                .fetchOne(PRODUCT_ITEM_TYPE);
        dto.setItemType(itemType != null ? itemType : "ingredient");
    }

    private List<ProductDTO> enrichForSupplier(List<ProductDTO> products, int supplierId) {
        enrichWithAverageStockPrice(products);
        Map<Integer, ? extends Record> offers = dsl.select(
                        PS_PRODUCT_ID,
                        PS_DEFAULT_PRICE,
                        PS_PURCHASE_UNIT,
                        PS_PURCHASE_UNIT_FACTOR,
                        PS_SUPPLIER_SKU
                )
                .from(PRODUCT_SUPPLIER)
                .where(PS_SUPPLIER_ID.eq(supplierId))
                .fetchMap(PS_PRODUCT_ID);
        for (ProductDTO product : products) {
            Record offer = offers.get(product.getProductId());
            if (offer == null) continue;
            product.setSupplierId(supplierId);
            product.setSupplierPrice(offer.get(PS_DEFAULT_PRICE));
            product.setSupplierUnit(offer.get(PS_PURCHASE_UNIT));
            product.setSupplierUnitFactor(offer.get(PS_PURCHASE_UNIT_FACTOR));
            product.setSupplierSku(offer.get(PS_SUPPLIER_SKU));
        }
        return products;
    }

    private Map<Integer, BigDecimal> loadAverageStockPriceMap() {
        Field<BigDecimal> quantity = DSL.sum(PW_QUANTITY.cast(BigDecimal.class));
        Field<BigDecimal> inventoryValue = DSL.sum(PW_INVENTORY_VALUE);
        Table<?> warehouse = DSL.table(DSL.name("sales", "warehouse"));
        Field<Integer> warehouseId = DSL.field(DSL.name("warehouseid"), Integer.class);
        Field<Boolean> isMain = DSL.field(DSL.name("is_main"), Boolean.class);
        Integer mainWarehouseId = dsl.select(warehouseId)
                .from(warehouse)
                .where(isMain.eq(true))
                .limit(1)
                .fetchOne(warehouseId);
        org.jooq.Condition balanceCondition = PW_QUANTITY.gt(0.0);
        if (mainWarehouseId != null) {
            balanceCondition = balanceCondition.and(PW_WAREHOUSE_ID.eq(mainWarehouseId));
        }
        var rows = dsl.select(PW_PRODUCT_ID, quantity, inventoryValue)
                .from(PRODUCT_WAREHOUSE)
                .where(balanceCondition)
                .groupBy(PW_PRODUCT_ID)
                .fetch();

        Map<Integer, BigDecimal> result = new HashMap<>();
        for (var r : rows) {
            Integer productId = r.get(PW_PRODUCT_ID);
            BigDecimal currentQuantity = r.get(quantity);
            BigDecimal currentValue = r.get(inventoryValue);
            if (productId == null || currentQuantity == null || currentValue == null
                    || currentQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            result.put(productId, currentValue.divide(currentQuantity, 6, RoundingMode.HALF_UP));
        }
        return result;
    }

}
