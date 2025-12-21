package com.shakur.cafehelp.DTO;


public class TimeDelayRequest {
    private Double delayMinutes;

    public TimeDelayRequest() {
    }

    public TimeDelayRequest(Double delayMinutes) {
        this.delayMinutes = delayMinutes;
    }

    public Double getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(Double delayMinutes) {
        this.delayMinutes = delayMinutes;
    }
}