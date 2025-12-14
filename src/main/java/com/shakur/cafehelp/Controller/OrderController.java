package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.OrderDTO;
import com.shakur.cafehelp.Service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Создание нового заказа
    @PostMapping
    public OrderDTO createOrder(@RequestBody OrderDTO order) {
        return orderService.createOrder(order);
    }

    // Получение заказа по ID
    @GetMapping("/{id}")
    public OrderDTO getOrderById(@PathVariable int id) {
        return orderService.getOrderById(id);
    }

    // Получение заказов по ClientId
    @GetMapping("/client/{clientId}")
    public List<OrderDTO> getOrdersByClientId(@PathVariable int clientId) {
        return orderService.getOrdersByClientId(clientId);
    }

    // Получение заказов по дате
    @GetMapping("/date/{date}")
    public List<OrderDTO> getOrdersByDate(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return orderService.getOrdersByDate(date);
    }

    // Получение заказов по дате и ClientId
    @GetMapping("/client/{clientId}/date/{date}")
    public List<OrderDTO> getOrdersByDateAndClientId(
            @PathVariable int clientId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return orderService.getOrdersByDateAndClientId(date, clientId);
    }

    // Получение заказов по статусу
    @GetMapping("/status/{status}")
    public List<OrderDTO> getOrdersByStatus(@PathVariable Boolean status) {
        return orderService.getOrdersByStatus(status);
    }
}
