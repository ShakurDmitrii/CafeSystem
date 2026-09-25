package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.ConsumableRuleDTO;
import com.shakur.cafehelp.DTO.ConsumablePreviewRequestDTO;
import com.shakur.cafehelp.Service.ConsumableService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/consumables")
public class ConsumableController {
    private final ConsumableService consumableService;

    public ConsumableController(ConsumableService consumableService) {
        this.consumableService = consumableService;
    }

    @GetMapping
    public List<ConsumableRuleDTO> getCatalog() {
        return consumableService.getCatalog();
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody ConsumablePreviewRequestDTO request) {
        var prepared = consumableService.prepare(
                request != null ? request.getPersonCount() : 1,
                request != null ? request.getItems() : List.of(),
                request != null ? request.getConsumables() : List.of(),
                "order-preview"
        );
        return Map.of(
                "personCount", prepared.personCount(),
                "consumables", consumableService.toDtos(prepared),
                "surchargeTotal", prepared.surchargeTotal()
        );
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ConsumableRuleDTO> configure(
            @PathVariable int productId,
            @RequestBody ConsumableRuleDTO dto
    ) {
        return ResponseEntity.ok(consumableService.configure(productId, dto));
    }
}
