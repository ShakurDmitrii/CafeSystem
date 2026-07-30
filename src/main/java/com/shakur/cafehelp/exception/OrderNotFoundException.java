package com.shakur.cafehelp.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(int orderId) {
        super("Заказ с id " + orderId + " не найден");
    }
}

