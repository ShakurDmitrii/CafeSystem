package com.shakur.cafehelp.exception;

public class ImageValidationException extends IllegalArgumentException {
    private final String code;

    public ImageValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
