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

    public List<ProductDTO> getAllProducts(){
        return dsl.selectFrom(Product.PRODUCT)
                .fetch()
                .stream()
                .map(record ->{
                    ProductDTO dto = new ProductDTO();
                    dto.productId = record.getProductid();
                    dto.productName = record.getProductname();
                    dto.productPrice = record.getProductprice();
                    dto.supplierId = record.getSupplierid();
                    dto.waste = record.getWaste();
                    return dto;
                }).toList();
    }
    public ProductDTO createProduct(ProductDTO dto){
        ProductRecord record = dsl.fetchOne(Product.PRODUCT);
        assert record != null;
        record.setProductid(dto.productId);
        record.setProductname(dto.productName);
        record.setProductprice(dto.productPrice);
        record.setSupplierid(dto.supplierId);
        record.setWaste(dto.waste);
        record.store();
        return dto;
    }
}
