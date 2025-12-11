package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ProductDTO;
import jooqdata.tables.Product;
import jooqdata.tables.records.PersonRecord;
import jooqdata.tables.records.ProductRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {
    private DSLContext dsl;
    public ProductService(DSLContext dsl){this.dsl = dsl;}


    public List<ProductDTO> getProducts() {
        return dsl.selectFrom(Product.PRODUCT)
                .fetch()
                .stream()
                .map(record ->{
                    ProductDTO dto = new ProductDTO();
                    dto.productName = record.getProductname();
                    dto.productId = record.getProductid();
                    dto.productPrice = record.getProductprice();
                    dto.isFavorite = record.getIsfavourite();
                    dto.waste = record.getWaste();
                    dto.supplierId = record.getSupplierid();
                    return dto;
                }).toList();
    }
    public List<ProductDTO> getAllFavoriteSupplierProduct(int supplierId){
        return dsl.selectFrom(Product.PRODUCT)
                .where(Product.PRODUCT.SUPPLIERID.eq(supplierId))
                .and(Product.PRODUCT.ISFAVOURITE.eq(true))
                .stream()
                .map(record -> {
                    ProductDTO dto = new ProductDTO();
                    dto.productId = record.getProductid();
                    dto.productName = record.getProductname();
                    dto.productPrice = record.getProductprice();
                    dto.supplierId = record.getSupplierid();
                    dto.waste = record.getWaste();
                    dto.isFavorite = record.getIsfavourite();
                    return dto;
                }).toList();

    }
    public List<ProductDTO> getAllSupplierProducts(int id){
        return dsl.selectFrom(Product.PRODUCT)
                .where(Product.PRODUCT.SUPPLIERID.eq(id))
                .fetch()
                .stream()
                .map(record ->{
                    ProductDTO dto = new ProductDTO();
                    dto.productId = record.getProductid();
                    dto.productName = record.getProductname();
                    dto.productPrice = record.getProductprice();
                    dto.supplierId = record.getSupplierid();
                    dto.waste = record.getWaste();
                    dto.isFavorite = record.getIsfavourite();
                    return dto;
                }).toList();
    }
    public ProductDTO createProduct(ProductDTO dto) {
        // создаём новый record
        ProductRecord record = dsl.newRecord(Product.PRODUCT);

        // заполняем поля
        record.setSupplierid(dto.supplierId);
        record.setProductname(dto.productName);
        record.setProductprice(dto.productPrice);
        record.setWaste(dto.waste);
        record.setIsfavourite(dto.isFavorite);

        // сохраняем в БД
        record.store();

        // возвращаем DTO с новым ID
        dto.productId = record.getProductid();
        return dto;
    }

}
