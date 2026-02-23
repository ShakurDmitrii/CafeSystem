package com.shakur.cafehelp.Controller.PyController;

import com.shakur.cafehelp.DTO.ConsignmentNoteDTO;
import com.shakur.cafehelp.DTO.ProductDTO;
import com.shakur.cafehelp.DTO.SupplierDTO;
import com.shakur.cafehelp.Service.ConsignmentNoteService;
import com.shakur.cafehelp.Service.ProductService;
import com.shakur.cafehelp.Service.SupplierService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/consignmentNote")
public class ConsignmentPrintController {

    private final ConsignmentNoteService service;
    private final RestTemplate restTemplate;
    private final SupplierService supplierService;
    private final ProductService productService;
    private final String pythonApiUrl;

    public ConsignmentPrintController(
            ConsignmentNoteService service,
            RestTemplate restTemplate,
            SupplierService supplierService,
            ProductService productService,
            @Value("${python.api.url:http://localhost:8000}") String pythonApiUrl
    ) {
        this.service = service;
        this.restTemplate = restTemplate;
        this.supplierService = supplierService;
        this.productService = productService;
        this.pythonApiUrl = pythonApiUrl;
    }

    @PostMapping("/print/{id}")
    public ResponseEntity<?> printConsignment(@PathVariable Integer id) {
        try {
            // Получаем накладную с товарами
            ConsignmentNoteDTO dto = service.getConsignmentWithProducts(id);
            if (dto == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Накладная не найдена"));
            }
            if (dto.items == null || dto.items.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "В накладной нет товаров для печати"));
            }

            // Получаем все товары (чтобы подтянуть цену нетто)
            List<ProductDTO> productDTOList = productService.getProducts();

            // Получаем информацию о поставщике (fallback, если не найден)
            String supplierName = "Неизвестный поставщик";
            try {
                SupplierDTO supplier = supplierService.getSupplierById(dto.supplierId);
                if (supplier != null && supplier.supplierName != null && !supplier.supplierName.isBlank()) {
                    supplierName = supplier.supplierName;
                }
            } catch (Exception ignored) {
            }

            // Формируем список товаров с безопасными fallback-значениями.
            List<Map<String, Object>> items = dto.items.stream().map(p -> {
                Map<String, Object> item = new HashMap<>();

                ProductDTO prod = productDTOList.stream()
                        .filter(pr -> pr.productId == p.productId)
                        .findFirst()
                        .orElse(null);

                BigDecimal quantity = BigDecimal.valueOf(p.quantity != null ? p.quantity : 0);
                BigDecimal price = (prod != null && prod.productPrice != null) ? prod.productPrice : BigDecimal.ZERO;
                BigDecimal sum = price.multiply(quantity);

                item.put("name", (p.productName != null && !p.productName.isBlank()) ? p.productName : ("Товар #" + p.productId));
                item.put("quantity", quantity);
                item.put("price", price);
                item.put("sum", sum);
                return item;
            }).toList();

            BigDecimal computedTotal = items.stream()
                    .map(m -> (BigDecimal) m.get("sum"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Формируем payload для Python
            Map<String, Object> payload = new HashMap<>();
            payload.put("consignmentId", dto.consignmentId);
            payload.put("supplierName", supplierName);
            payload.put("date", dto.date != null ? dto.date.toString() : null);
            payload.put("total", dto.amount != null ? dto.amount : computedTotal.doubleValue());
            payload.put("items", items);

            // Отправляем на Python
            String printUrl = pythonApiUrl.endsWith("/")
                    ? pythonApiUrl + "print"
                    : pythonApiUrl + "/print";
            restTemplate.postForEntity(printUrl, payload, Object.class);

            return ResponseEntity.ok(Map.of("status", "printed", "consignmentId", dto.consignmentId));
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "Python-сервис печати недоступен: " + e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Ошибка печати: " + e.getMessage()));
        }
    }

}
