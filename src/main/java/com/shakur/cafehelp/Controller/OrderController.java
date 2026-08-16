package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.OrderDTO;
import com.shakur.cafehelp.DTO.OrderDishDTO;
import com.shakur.cafehelp.DTO.OrderEditRequestDTO;
import com.shakur.cafehelp.DTO.TimeDelayRequest;
import com.shakur.cafehelp.Service.OrderService;
import com.shakur.cafehelp.exception.InvalidOrderRequestException;
import com.shakur.cafehelp.exception.OrderNotFoundException;
import com.shakur.cafehelp.exception.OrderStateConflictException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
        System.out.println("=== СОЗДАНИЕ ЗАКАЗА ===");
        System.out.println("Получен DTO: " + order);
        System.out.println("duty: " + order.getDuty());
        System.out.println("debt_payment_date: " + order.getDebt_payment_date());
        System.out.println("Тип debt_payment_date: " +
                (order.getDebt_payment_date() != null ?
                        order.getDebt_payment_date().getClass().getName() : "null"));
        try {
            OrderDTO createdOrder = orderService.createOrder(order);
            return ResponseEntity.ok(createdOrder);
        } catch (InvalidOrderRequestException | IllegalArgumentException e) {
            return orderError(HttpStatus.BAD_REQUEST, "INVALID_ORDER", e.getMessage());
        } catch (OrderNotFoundException e) {
            return orderError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", e.getMessage());
        } catch (OrderStateConflictException e) {
            return orderError(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", e.getMessage());
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

    @PatchMapping("/{orderId}/payment")
    public ResponseEntity<?> updatePayment(
            @PathVariable int orderId,
            @RequestBody PaymentUpdateRequest request
    ) {
        try {
            OrderDTO updatedOrder = orderService.updateOrderPayment(orderId, request.getPaymentType(), request.getPaid());
            return ResponseEntity.ok(updatedOrder);
        } catch (OrderStateConflictException e) {
            return orderError(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Ошибка обновления оплаты: " + e.getMessage()));
        }
    }

    @PatchMapping("/{orderId}/issue")
    public ResponseEntity<?> markIssued(@PathVariable int orderId) {
        try {
            return ResponseEntity.ok(orderService.markOrderIssued(orderId));
        } catch (OrderStateConflictException e) {
            return orderError(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Ошибка выдачи заказа: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Заменить состав и редактируемые реквизиты заказа",
            description = "Разрешено только для неоплаченного, невыданного и неотменённого заказа открытой смены. Сумма пересчитывается сервером."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Заказ обновлён"),
            @ApiResponse(responseCode = "400", description = "Некорректный состав или реквизиты"),
            @ApiResponse(responseCode = "404", description = "Заказ не найден"),
            @ApiResponse(responseCode = "409", description = "Состояние или версия заказа не позволяют изменение")
    })
    @PutMapping("/{orderId}")
    public ResponseEntity<?> replaceOrder(
            @PathVariable int orderId,
            @RequestBody OrderEditRequestDTO request
    ) {
        try {
            return ResponseEntity.ok(orderService.replaceEditableOrder(orderId, request));
        } catch (InvalidOrderRequestException e) {
            return orderError(HttpStatus.BAD_REQUEST, "INVALID_ORDER", e.getMessage());
        } catch (OrderNotFoundException e) {
            return orderError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", e.getMessage());
        } catch (OrderStateConflictException e) {
            return orderError(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", e.getMessage());
        }
    }

    @Operation(
            summary = "Отменить созданный заказ",
            description = "Выполняет мягкую отмену без возврата продуктов на склад. Для оплаченного заказа атомарно создаёт отдельное событие фискального возврата; причина обязательна. Повторный запрос идемпотентен."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Заказ отменён или уже был отменён"),
            @ApiResponse(responseCode = "404", description = "Заказ не найден"),
            @ApiResponse(responseCode = "409", description = "Заказ уже изменён либо неоплаченный заказ выдан/относится к закрытой смене")
    })
    @DeleteMapping("/{orderId}")
    public ResponseEntity<?> cancelOrder(
            @PathVariable int orderId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Integer expectedVersion
    ) {
        try {
            return ResponseEntity.ok(orderService.cancelOrder(orderId, reason, expectedVersion));
        } catch (InvalidOrderRequestException e) {
            return orderError(HttpStatus.BAD_REQUEST, "INVALID_ORDER", e.getMessage());
        } catch (OrderNotFoundException e) {
            return orderError(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", e.getMessage());
        } catch (OrderStateConflictException e) {
            return orderError(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", e.getMessage());
        }
    }

    public static class StatusUpdateRequest {
        private Boolean status;
        public Boolean getStatus() { return status; }
        public void setStatus(Boolean status) { this.status = status; }
    }

    public static class PaymentUpdateRequest {
        private String paymentType;
        private Boolean paid;

        public String getPaymentType() {
            return paymentType;
        }

        public void setPaymentType(String paymentType) {
            this.paymentType = paymentType;
        }

        public Boolean getPaid() {
            return paid;
        }

        public void setPaid(Boolean paid) {
            this.paid = paid;
        }
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
    public ResponseEntity<?> addDishesToOrder(@RequestBody List<OrderDishDTO> items, @RequestParam int orderId) {
        try {
            for (OrderDishDTO d : items) {
                orderService.addDishToOrder(orderId, d.getDishID(), d.getQty());
            }
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (OrderStateConflictException e) {
            return orderError(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Ошибка добавления блюд: " + e.getMessage()));
        }
    }

    @PostMapping("/{orderId}/kitchen-payload")
    public ResponseEntity<?> getKitchenPayload(
            @PathVariable int orderId,
            @RequestBody(required = false) PrintKitchenRequest request
    ) {
        try {
            String paymentType = request != null ? request.getPaymentType() : null;
            Double deliveryCost = request != null ? request.getDeliveryCost() : null;
            String deliveryPhone = request != null ? request.getDeliveryPhone() : null;
            String deliveryAddress = request != null ? request.getDeliveryAddress() : null;

            Map<String, Object> payload = orderService.getOrderKitchenPrintPayload(
                    orderId,
                    paymentType,
                    deliveryCost,
                    deliveryPhone,
                    deliveryAddress
            );
            return ResponseEntity.ok(payload);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Ошибка подготовки данных для печати: " + e.getMessage()));
        }
    }

    public static class PrintKitchenRequest {
        private String paymentType;
        private Double deliveryCost;
        private String deliveryPhone;
        private String deliveryAddress;

        public String getPaymentType() {
            return paymentType;
        }

        public void setPaymentType(String paymentType) {
            this.paymentType = paymentType;
        }

        public Double getDeliveryCost() {
            return deliveryCost;
        }

        public void setDeliveryCost(Double deliveryCost) {
            this.deliveryCost = deliveryCost;
        }

        public String getDeliveryPhone() {
            return deliveryPhone;
        }

        public void setDeliveryPhone(String deliveryPhone) {
            this.deliveryPhone = deliveryPhone;
        }

        public String getDeliveryAddress() {
            return deliveryAddress;
        }

        public void setDeliveryAddress(String deliveryAddress) {
            this.deliveryAddress = deliveryAddress;
        }
    }

    private ResponseEntity<Map<String, String>> orderError(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "code", code,
                "message", message != null ? message : status.getReasonPhrase()
        ));
    }

}
