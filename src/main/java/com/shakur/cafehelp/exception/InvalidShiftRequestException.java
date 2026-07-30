package com.shakur.cafehelp.exception;

public class InvalidShiftRequestException extends IllegalArgumentException {
    public InvalidShiftRequestException(String message) {
        super(message);
    }
}
