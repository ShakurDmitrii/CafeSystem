package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.DishDTO;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DishServiceReadContractTest {

    @Test
    void calculatedCostEnrichmentDoesNotWriteDuringMenuRead() {
        DSLContext dsl = mock(DSLContext.class);
        RecipeCostService recipeCostService = mock(RecipeCostService.class);
        DishService service = new DishService(dsl, recipeCostService, mock(DishSetService.class));
        DishDTO dish = new DishDTO();
        dish.setDishId(13);
        dish.setFirstCost(0.0);

        when(recipeCostService.calculateDishCost(13)).thenReturn(102.83);

        DishDTO result = ReflectionTestUtils.invokeMethod(
                service,
                "enrichWithCalculatedFirstCost",
                dish
        );

        assertThat(result).isSameAs(dish);
        assertThat(result.getFirstCost()).isEqualTo(102.83);
        verifyNoInteractions(dsl);
    }
}
