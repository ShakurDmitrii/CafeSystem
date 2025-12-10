package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.FavoriteProductDTO;
import jooqdata.tables.Favoriteproduct;
import jooqdata.tables.records.FavoriteproductRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FavoriteProductService {
    private DSLContext dsl;
    public FavoriteProductService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public FavoriteProductDTO createFavoriteProduct(FavoriteProductDTO dto) {
        FavoriteproductRecord record = dsl.newRecord(Favoriteproduct.FAVORITEPRODUCT);
        record.setId(dto.getId());
        record.setProductid(dto.getProductId());
        record.setSupplierid(dto.getSupplierId());
        record.setDate(dto.getDate());
        record.setPrice(dto.getPrice());
        return  dto;
    }

    public List<FavoriteProductDTO> findAll() {
        return dsl.selectFrom(Favoriteproduct.FAVORITEPRODUCT)
                .fetch()
                .stream()
                .map(record ->{
                    FavoriteProductDTO dto = new FavoriteProductDTO();
                    dto.setId(record.getId());
                    dto.setProductId(record.getProductid());
                    dto.setSupplierId(record.getSupplierid());
                    dto.setDate(record.getDate());
                    dto.setPrice(record.getPrice());
                    return dto;
                }).toList();
    }

    public FavoriteProductDTO findById(int id) {
        return dsl.selectFrom(Favoriteproduct.FAVORITEPRODUCT)
                .where(Favoriteproduct.FAVORITEPRODUCT.PRODUCTID.eq(id))
                .fetchOptional()
                .map(record ->{
                    FavoriteProductDTO dto = new FavoriteProductDTO();
                    dto.setId(record.getId());
                    dto.setDate(record.getDate());
                    dto.setSupplierId(record.getSupplierid());
                    dto.setProductId(record.getProductid());
                    dto.setPrice(record.getPrice());
                    return dto;
                }).orElseThrow(()->new RuntimeException("Not found favorite product"));
    }
}
