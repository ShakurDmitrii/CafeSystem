package com.shakur.cafehelp.DTO;

import javax.xml.crypto.Data;
import java.time.LocalDate;

public class ClientDutyDTO {
    public int clientId;
    public String clientName;
    public String number;
    public double duty;
    public LocalDate date;

    public int getClientId() {
        return clientId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public double getDuty() {
        return duty;
    }

    public void setDuty(double duty) {
        this.duty = duty;
    }
}
