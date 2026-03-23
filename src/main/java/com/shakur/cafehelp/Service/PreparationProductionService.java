package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.PreparationDTO;
import com.shakur.cafehelp.DTO.PreparationProductionRequestDTO;
import com.shakur.cafehelp.DTO.PreparationProductionResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PreparationProductionService {
    private final PreparationService preparationService;
    private final RecipeRequirementService recipeRequirementService;
    private final WareHouseService wareHouseService;
    private final ProductService productService;

    public PreparationProductionService(
            PreparationService preparationService,
            RecipeRequirementService recipeRequirementService,
            WareHouseService wareHouseService,
            ProductService productService
    ) {
        this.preparationService = preparationService;
        this.recipeRequirementService = recipeRequirementService;
        this.wareHouseService = wareHouseService;
        this.productService = productService;
    }

    @Transactional
    public PreparationProductionResponseDTO produce(int preparationId, PreparationProductionRequestDTO request) {
        PreparationDTO preparation = preparationService.getById(preparationId);
        if (preparation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Заготовка не найдена");
        }

        Integer warehouseId = request != null ? request.getWarehouseId() : null;
        if (warehouseId == null || warehouseId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouseId обязателен");
        }

        double batchCount = request != null && request.getBatchCount() != null ? request.getBatchCount() : 1.0;
        if (batchCount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Количество партий должно быть больше 0");
        }

        RecipeRequirementService.RequirementSet requirements =
                recipeRequirementService.buildForPreparation(preparationId, batchCount);

        validateAvailability(warehouseId, requirements);

        for (Map.Entry<Integer, Double> entry : requirements.productRequirements().entrySet()) {
            if (!wareHouseService.adjustQuantity(warehouseId, entry.getKey(), -entry.getValue())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Не удалось списать продукт #" + entry.getKey());
            }
        }

        for (Map.Entry<Integer, Double> entry : requirements.preparationRequirements().entrySet()) {
            if (!wareHouseService.adjustPreparationQuantity(warehouseId, entry.getKey(), -entry.getValue())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Не удалось списать заготовку #" + entry.getKey());
            }
        }

        double producedQuantity = (preparation.getOutputWeight() != null ? preparation.getOutputWeight() : 0.0) * batchCount;
        if (!wareHouseService.adjustPreparationQuantity(warehouseId, preparationId, producedQuantity)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Не удалось оприходовать заготовку");
        }

        PreparationProductionResponseDTO response = new PreparationProductionResponseDTO();
        response.setPreparationId(preparationId);
        response.setPreparationName(preparation.getPreparationName());
        response.setWarehouseId(warehouseId);
        response.setBatchCount(batchCount);
        response.setProducedQuantity(producedQuantity);
        response.setWarehouseQuantityAfter(wareHouseService.getAvailablePreparationQuantity(warehouseId, preparationId));
        return response;
    }

    private void validateAvailability(int warehouseId, RecipeRequirementService.RequirementSet requirements) {
        Map<Integer, String> productNames = productService.getProducts().stream()
                .filter(product -> product.getProductName() != null)
                .collect(java.util.stream.Collectors.toMap(
                        product -> product.getProductId(),
                        product -> product.getProductName(),
                        (left, right) -> left
                ));

        List<String> missing = new ArrayList<>();

        for (Map.Entry<Integer, Double> entry : requirements.productRequirements().entrySet()) {
            double available = wareHouseService.getAvailableQuantity(warehouseId, entry.getKey());
            if (available + 1e-6 < entry.getValue()) {
                String name = productNames.getOrDefault(entry.getKey(), "Продукт #" + entry.getKey());
                missing.add(name + " (" + formatQty(available) + "/" + formatQty(entry.getValue()) + " г)");
            }
        }

        for (Map.Entry<Integer, Double> entry : requirements.preparationRequirements().entrySet()) {
            double available = wareHouseService.getAvailablePreparationQuantity(warehouseId, entry.getKey());
            PreparationDTO prep = preparationService.getById(entry.getKey());
            String name = prep != null ? prep.getPreparationName() : ("Заготовка #" + entry.getKey());
            if (available + 1e-6 < entry.getValue()) {
                missing.add(name + " (" + formatQty(available) + "/" + formatQty(entry.getValue()) + " г)");
            }
        }

        if (!missing.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Не хватает ингредиентов на складе: " + String.join(", ", missing)
            );
        }
    }

    private String formatQty(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }
}
