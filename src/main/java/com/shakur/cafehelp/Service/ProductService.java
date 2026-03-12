package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ProductDTO;
import jooqdata.tables.Product;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {
    private DSLContext dsl;
    private static final Field<String> PRODUCT_UNIT = DSL.field(DSL.name("unit"), String.class);
    private static final Field<String> PRODUCT_BASE_UNIT = DSL.field(DSL.name("base_unit"), String.class);
    private static final Field<BigDecimal> PRODUCT_UNIT_FACTOR = DSL.field(DSL.name("unit_factor"), BigDecimal.class);
    private static final Field<String> PRODUCT_IMAGE_URL = DSL.field(DSL.name("image_url"), String.class);
    private static final org.jooq.Table<?> PRODUCT_SUPPLIER = DSL.table(DSL.name("sales", "product_supplier"));
    private static final Field<Integer> PS_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<Integer> PS_SUPPLIER_ID = DSL.field(DSL.name("supplier_id"), Integer.class);
    private volatile Boolean unitColumnsPresent = null;
    private volatile Boolean imageColumnPresent = null;
    private static final Field<Integer> MOVEMENT_PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<BigDecimal> MOVEMENT_QTY_IN = DSL.field(DSL.name("qty_in"), BigDecimal.class);
    private static final Field<BigDecimal> MOVEMENT_AMOUNT = DSL.field(DSL.name("amount"), BigDecimal.class);
    private static final org.jooq.Table<?> STOCK_MOVEMENTS = DSL.table(DSL.name("sales", "stock_movements"));

    public ProductService(DSLContext dsl){this.dsl = dsl;}


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
                    .join(PRODUCT_SUPPLIER).on(PS_PRODUCT_ID.eq(Product.PRODUCT.PRODUCTID))
                    .where(PS_SUPPLIER_ID.eq(supplierId))
                    .and(Product.PRODUCT.ISFAVOURITE.eq(true))
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
                .join(PRODUCT_SUPPLIER).on(PS_PRODUCT_ID.eq(Product.PRODUCT.PRODUCTID))
                .where(PS_SUPPLIER_ID.eq(supplierId))
                .and(Product.PRODUCT.ISFAVOURITE.eq(true))
                .fetch()
                .stream()
                .map(this::toDto)
                .toList();
        return enrichWithAverageStockPrice(result);

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
                    .join(PRODUCT_SUPPLIER).on(PS_PRODUCT_ID.eq(Product.PRODUCT.PRODUCTID))
                    .where(PS_SUPPLIER_ID.eq(id))
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
                .join(PRODUCT_SUPPLIER).on(PS_PRODUCT_ID.eq(Product.PRODUCT.PRODUCTID))
                .where(PS_SUPPLIER_ID.eq(id))
                .fetch()
                .stream()
                .map(this::toDto)
                .toList();
        return enrichWithAverageStockPrice(result);
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
        return dto;
    }
    public ProductDTO createProduct(ProductDTO dto) {
        String unit = dto.unit != null && !dto.unit.isBlank() ? dto.unit.trim().toLowerCase() : "g";
        String baseUnit = dto.baseUnit != null && !dto.baseUnit.isBlank() ? dto.baseUnit.trim().toLowerCase() : unit;
        BigDecimal unitFactor = dto.unitFactor != null && dto.unitFactor.compareTo(BigDecimal.ZERO) > 0
                ? dto.unitFactor
                : BigDecimal.ONE;

        Integer supplierId = dto.supplierId;
        String normalizedName = dto.productName != null ? dto.productName.trim() : "";
        if (!normalizedName.isEmpty()) {
            Integer existingId = dsl.select(Product.PRODUCT.PRODUCTID)
                    .from(Product.PRODUCT)
                    .where(DSL.lower(Product.PRODUCT.PRODUCTNAME).eq(normalizedName.toLowerCase()))
                    .limit(1)
                    .fetchOne(Product.PRODUCT.PRODUCTID);
            if (existingId != null) {
                if (supplierId != null) {
                    linkProductToSupplier(existingId, supplierId);
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
                    .set(Product.PRODUCT.SUPPLIERID, dto.supplierId)
                    .set(Product.PRODUCT.PRODUCTNAME, dto.productName)
                    .set(Product.PRODUCT.PRODUCTPRICE, dto.productPrice)
                    .set(Product.PRODUCT.WASTE, dto.waste)
                    .set(Product.PRODUCT.ISFAVOURITE, dto.isFavorite)
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
                    .set(Product.PRODUCT.SUPPLIERID, dto.supplierId)
                    .set(Product.PRODUCT.PRODUCTNAME, dto.productName)
                    .set(Product.PRODUCT.PRODUCTPRICE, dto.productPrice)
                    .set(Product.PRODUCT.WASTE, dto.waste)
                    .set(Product.PRODUCT.ISFAVOURITE, dto.isFavorite)
                    .returning(Product.PRODUCT.PRODUCTID)
                    .fetchOne(Product.PRODUCT.PRODUCTID);
        }

        dto.productId = id != null ? id : 0;
        dto.unit = unit;
        dto.baseUnit = baseUnit;
        dto.unitFactor = unitFactor;
        if (!hasImageColumn()) dto.imageUrl = null;
        if (supplierId != null && dto.productId != 0) {
            linkProductToSupplier(dto.productId, supplierId);
        }
        return dto;
    }

    private void linkProductToSupplier(int productId, int supplierId) {
        dsl.insertInto(PRODUCT_SUPPLIER)
                .columns(PS_PRODUCT_ID, PS_SUPPLIER_ID)
                .values(productId, supplierId)
                .onConflict(PS_PRODUCT_ID, PS_SUPPLIER_ID)
                .doNothing()
                .execute();
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
        for (ProductDTO dto : products) {
            dto.setAverageStockPrice(avgByProduct.get(dto.getProductId()));
        }
        return products;
    }

    private Map<Integer, BigDecimal> loadAverageStockPriceMap() {
        var rows = dsl.select(MOVEMENT_PRODUCT_ID, DSL.sum(MOVEMENT_QTY_IN), DSL.sum(MOVEMENT_AMOUNT))
                .from(STOCK_MOVEMENTS)
                .where(MOVEMENT_QTY_IN.gt(BigDecimal.ZERO))
                .groupBy(MOVEMENT_PRODUCT_ID)
                .fetch();

        Map<Integer, BigDecimal> result = new HashMap<>();
        for (var r : rows) {
            Integer productId = r.get(MOVEMENT_PRODUCT_ID);
            BigDecimal qtyIn = r.get(DSL.sum(MOVEMENT_QTY_IN));
            BigDecimal amount = r.get(DSL.sum(MOVEMENT_AMOUNT));
            if (productId == null || qtyIn == null || amount == null || qtyIn.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            result.put(productId, amount.divide(qtyIn, 4, RoundingMode.HALF_UP));
        }
        return result;
    }

}
