package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ProductDTO;
import jooqdata.tables.Product;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {
    private DSLContext dsl;
    private static final Field<String> PRODUCT_UNIT = DSL.field(DSL.name("unit"), String.class);
    private static final Field<String> PRODUCT_BASE_UNIT = DSL.field(DSL.name("base_unit"), String.class);
    private static final Field<BigDecimal> PRODUCT_UNIT_FACTOR = DSL.field(DSL.name("unit_factor"), BigDecimal.class);
    private volatile Boolean unitColumnsPresent = null;

    public ProductService(DSLContext dsl){this.dsl = dsl;}


    public List<ProductDTO> getProducts() {
        if (!hasUnitColumns()) {
            return dsl.selectFrom(Product.PRODUCT)
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
                        return dto;
                    }).toList();
        }
        return dsl.select(
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
    }
    public List<ProductDTO> getAllFavoriteSupplierProduct(int supplierId){
        if (!hasUnitColumns()) {
            return dsl.selectFrom(Product.PRODUCT)
                    .where(Product.PRODUCT.SUPPLIERID.eq(supplierId))
                    .and(Product.PRODUCT.ISFAVOURITE.eq(true))
                    .fetch()
                    .stream()
                    .map(record -> {
                        ProductDTO dto = new ProductDTO();
                        dto.productId = record.getProductid();
                        dto.productName = record.getProductname();
                        dto.productPrice = record.getProductprice();
                        dto.supplierId = record.getSupplierid();
                        dto.waste = record.getWaste();
                        dto.isFavorite = record.getIsfavourite();
                        dto.unit = "g";
                        dto.baseUnit = "g";
                        dto.unitFactor = BigDecimal.ONE;
                        return dto;
                    }).toList();
        }
        return dsl.select(
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
                .where(Product.PRODUCT.SUPPLIERID.eq(supplierId))
                .and(Product.PRODUCT.ISFAVOURITE.eq(true))
                .fetch()
                .stream()
                .map(this::toDto)
                .toList();

    }
    public List<ProductDTO> getAllSupplierProducts(int id){
        if (!hasUnitColumns()) {
            return dsl.selectFrom(Product.PRODUCT)
                    .where(Product.PRODUCT.SUPPLIERID.eq(id))
                    .fetch()
                    .stream()
                    .map(record -> {
                        ProductDTO dto = new ProductDTO();
                        dto.productId = record.getProductid();
                        dto.productName = record.getProductname();
                        dto.productPrice = record.getProductprice();
                        dto.supplierId = record.getSupplierid();
                        dto.waste = record.getWaste();
                        dto.isFavorite = record.getIsfavourite();
                        dto.unit = "g";
                        dto.baseUnit = "g";
                        dto.unitFactor = BigDecimal.ONE;
                        return dto;
                    }).toList();
        }
        return dsl.select(
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
                .where(Product.PRODUCT.SUPPLIERID.eq(id))
                .fetch()
                .stream()
                .map(this::toDto)
                .toList();
    }
    public ProductDTO getProductById(int id) {
        if (!hasUnitColumns()) {
            return dsl.selectFrom(Product.PRODUCT)
                    .where(Product.PRODUCT.PRODUCTID.eq(id))
                    .fetchOptional()
                    .map(record -> {
                        ProductDTO dto = new ProductDTO();
                        dto.productId = record.getProductid();
                        dto.productName = record.getProductname();
                        dto.productPrice = record.getProductprice();
                        dto.supplierId = record.getSupplierid();
                        dto.waste = record.getWaste();
                        dto.isFavorite = record.getIsfavourite();
                        dto.unit = "g";
                        dto.baseUnit = "g";
                        dto.unitFactor = BigDecimal.ONE;
                        return dto;
                    }).orElseThrow();
        }
        return dsl.select(
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
    }
    public ProductDTO createProduct(ProductDTO dto) {
        String unit = dto.unit != null && !dto.unit.isBlank() ? dto.unit.trim().toLowerCase() : "g";
        String baseUnit = dto.baseUnit != null && !dto.baseUnit.isBlank() ? dto.baseUnit.trim().toLowerCase() : unit;
        BigDecimal unitFactor = dto.unitFactor != null && dto.unitFactor.compareTo(BigDecimal.ZERO) > 0
                ? dto.unitFactor
                : BigDecimal.ONE;

        Integer id;
        if (hasUnitColumns()) {
            id = dsl.insertInto(Product.PRODUCT)
                    .set(Product.PRODUCT.SUPPLIERID, dto.supplierId)
                    .set(Product.PRODUCT.PRODUCTNAME, dto.productName)
                    .set(Product.PRODUCT.PRODUCTPRICE, dto.productPrice)
                    .set(Product.PRODUCT.WASTE, dto.waste)
                    .set(Product.PRODUCT.ISFAVOURITE, dto.isFavorite)
                    .set(PRODUCT_UNIT, unit)
                    .set(PRODUCT_BASE_UNIT, baseUnit)
                    .set(PRODUCT_UNIT_FACTOR, unitFactor)
                    .returning(Product.PRODUCT.PRODUCTID)
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
        return dto;
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
        return dto;
    }

}
