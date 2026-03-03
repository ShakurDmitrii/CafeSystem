package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ProductDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class UnitConversionService {

    private final ProductService productService;

    public UnitConversionService(ProductService productService) {
        this.productService = productService;
    }

    public BigDecimal toBaseQuantity(int productId, Double quantity) {
        if (quantity == null) return BigDecimal.ZERO;
        ProductDTO product = productService.getProductById(productId);
        BigDecimal factor = product.getUnitFactor() != null ? product.getUnitFactor() : BigDecimal.ONE;
        if (factor.compareTo(BigDecimal.ZERO) <= 0) factor = BigDecimal.ONE;
        return BigDecimal.valueOf(quantity).multiply(factor);
    }
}

