package com.shakur.cafehelp.exception;

public class ShiftNotFoundException extends RuntimeException {
    public ShiftNotFoundException(int shiftId) {
        super("Смена с id " + shiftId + " не найдена");
    }
}
