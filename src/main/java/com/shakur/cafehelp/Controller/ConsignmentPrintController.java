package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.ConsignmentNoteDTO;
import com.shakur.cafehelp.DTO.ProductDTO;
import com.shakur.cafehelp.DTO.SupplierDTO;
import com.shakur.cafehelp.Service.ConsignmentNoteService;
import com.shakur.cafehelp.Service.ProductService;
import com.shakur.cafehelp.Service.SupplierService;
import jooqdata.tables.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

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

    public ConsignmentPrintController(ConsignmentNoteService service, RestTemplate restTemplate, SupplierService supplierService, ProductService productService) {
        this.service = service;
        this.restTemplate = restTemplate;
        this.supplierService = supplierService;
        this.productService = productService;
    }

    @PostMapping("/print/{id}")
    public ResponseEntity<Void> printConsignment(@PathVariable Integer id) {
        // Получаем накладную с товарами
        ConsignmentNoteDTO dto = service.getConsignmentWithProducts(id);

        // Получаем все товары (чтобы подтянуть цену нетто)
        List<ProductDTO> productDTOList = productService.getProducts();

        // Получаем информацию о поставщике
        SupplierDTO supplier = supplierService.getSupplierById(dto.supplierId);

        // Формируем payload для Python
        Map<String, Object> payload = new HashMap<>();
        payload.put("consignmentId", dto.consignmentId);
        payload.put("supplierName", supplier.supplierName); // теперь supplierName есть
        payload.put("date", dto.date.toString()); // YYYY-MM-DD
        payload.put("total", dto.amount); // используем amount

        // Формируем список товаров с правильной ценой и суммой
        List<Map<String, Object>> items = dto.items.stream().map(p -> {
            Map<String, Object> item = new HashMap<>();

            // Находим цену нетто из productDTO по productId
            ProductDTO prod = productDTOList.stream()
                    .filter(pr -> pr.productId == p.productId)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Продукт не найден: " + p.productId));

            item.put("name", p.productName);
            item.put("quantity", p.quantity);
            item.put("price", prod.productPrice);
            BigDecimal quantity = BigDecimal.valueOf(p.quantity);//  цена
            BigDecimal price = prod.productPrice;
            BigDecimal sum = price.multiply(quantity);
            item.put("sum", sum); // сумма = цена * количество
            return item;
        }).toList();

        payload.put("items", items);

        // Отправляем на Python
        restTemplate.postForEntity("http://localhost:8000/print", payload, Void.class);

        return ResponseEntity.ok().build();
    }

}
