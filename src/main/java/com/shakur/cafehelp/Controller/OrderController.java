package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.OrderDTO;
import com.shakur.cafehelp.DTO.OrderDishDTO;
import com.shakur.cafehelp.DTO.TimeDelayRequest;
import com.shakur.cafehelp.Service.OrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Создание нового заказа
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody OrderDTO order) {
        try {
            OrderDTO createdOrder = orderService.createOrder(order);
            return ResponseEntity.ok(createdOrder);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    // Добавление задержки заказа
    @PatchMapping("/{orderId}/timeDelay")
    public OrderDTO addTimeDelay(
            @PathVariable("orderId") int orderId,
            @RequestBody TimeDelayRequest request) {
        return orderService.addTimeDelay(orderId, request.getDelayMinutes());
    }
    // Обновление статуса заказа
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Boolean> updateStatus(
            @PathVariable int orderId,
            @RequestBody StatusUpdateRequest request
    ) {
        Boolean updatedStatus = orderService.updateOrderStatus(orderId, request.getStatus());
        return ResponseEntity.ok(updatedStatus);
    }

    public static class StatusUpdateRequest {
        private Boolean status;
        public Boolean getStatus() { return status; }
        public void setStatus(Boolean status) { this.status = status; }
    }

    // 🔥 ВСЕ ЗАКАЗЫ
    @GetMapping
    public List<OrderDTO> getAllOrders() {
        return orderService.getOrders();
    }

    // Заказ по ID
    @GetMapping("/{id}")
    public OrderDTO getOrderById(@PathVariable int id) {
        return orderService.getOrderById(id);
    }

    // Заказы клиента
    @GetMapping("/client/{clientId}")
    public List<OrderDTO> getOrdersByClientId(@PathVariable int clientId) {
        return orderService.getOrdersByClientId(clientId);
    }

    // Заказы по дате
    @GetMapping("/date/{date}")
    public List<OrderDTO> getOrdersByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return orderService.getOrdersByDate(date);
    }

    // Заказы по клиенту и дате
    @GetMapping("/client/{clientId}/date/{date}")
    public List<OrderDTO> getOrdersByDateAndClientId(
            @PathVariable int clientId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return orderService.getOrdersByDateAndClientId(date, clientId);
    }

    // Заказы по статусу
    @GetMapping("/status/{status}")
    public List<OrderDTO> getOrdersByStatus(@PathVariable Boolean status) {
        return orderService.getOrdersByStatus(status);
    }
    @GetMapping("/shift/{shiftId}")
    public List<OrderDTO> getOrdersByShiftId(@PathVariable int shiftId) {
        return orderService.getOrdersByShift(shiftId);
    }

    @PostMapping("/orderToDish")
    public Map<String, String> addDishesToOrder(@RequestBody List<OrderDishDTO> items, @RequestParam int orderId) {
        for (OrderDishDTO d : items) {
            orderService.addDishToOrder(orderId, d.getDishID(), d.getQty());
        }
        return Map.of("status", "ok");
    }


}
