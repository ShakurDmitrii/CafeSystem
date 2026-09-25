package com.shakur.cafehelp.exception;

public class ImageStorageException extends RuntimeException {
    private final String code;

    public ImageStorageException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ImageStorageException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
