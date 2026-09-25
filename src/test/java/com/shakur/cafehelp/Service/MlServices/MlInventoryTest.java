package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.DTO.ProductDTO;
import com.shakur.cafehelp.DTO.ProductWarehouseDTO;
import com.shakur.cafehelp.Service.ProductService;
import com.shakur.cafehelp.Service.WareHouseService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MlInventoryTest {
    @Test
    void usesMainWarehouseValuePerBaseUnitAndExcludesConsumables() {
        var products = mock(ProductService.class);
        var warehouses = mock(WareHouseService.class);
        var rice = new ProductDTO();
        rice.productId = 1; rice.productName = "Рис"; rice.unit = "kg";
        rice.baseUnit = "g"; rice.unitFactor = new BigDecimal("1000");
        rice.productPrice = new BigDecimal("1000");
        var bag = new ProductDTO();
        bag.productId = 2; bag.productName = "Пакет"; bag.itemType = "consumable";
        var stock = new ProductWarehouseDTO();
        stock.productId = 1; stock.quantity = 120.0;
        stock.inventoryValue = new BigDecimal("1700");
        when(products.getProducts()).thenReturn(List.of(rice, bag));
        when(warehouses.getMainWarehouseId()).thenReturn(3);
        when(warehouses.getProductsOnWarehouse(3)).thenReturn(List.of(stock));
        var service = new InventoryService(mock(DSLContext.class), products, warehouses);
        assertThat(service.getAllIngredients()).singleElement().satisfies(ingredient -> {
            assertThat(ingredient.getUnit()).isEqualTo("g");
            assertThat(ingredient.getCostPerUnit()).isCloseTo(1700.0 / 120, org.assertj.core.data.Offset.offset(0.000001));
            assertThat(ingredient.getCurrentStock()).isEqualTo(120.0);
            assertThat(ingredient.getCostSource()).isEqualTo("warehouse_average");
            assertThat(ingredient.getWarehouseId()).isEqualTo(3);
        });
    }
}
