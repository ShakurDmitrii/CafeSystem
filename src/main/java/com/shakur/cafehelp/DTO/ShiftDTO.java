package com.shakur.cafehelp.DTO;

import javax.xml.crypto.Data;
import java.sql.Time;
import java.time.LocalTime;

public class ShiftDTO {
    public int shiftId;
    public Data data;
    public LocalTime startTime;
    public LocalTime endTime;
    public Double profit;
    public int expenses;
    public int consignmentNoteCode;
    public int personCode;

   public int getId(){
       return shiftId;
   }
   public void setId(int id){
       this.shiftId = id;
   }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Double getProfit() {
        return profit;
    }

    public void setProfit(Double profit) {
        this.profit = profit;
    }

    public int getExpenses() {
        return expenses;
    }

    public void setExpenses(int expenses) {
        this.expenses = expenses;
    }

    public int getConsignmentNoteCode() {
        return consignmentNoteCode;
    }

    public void setConsignmentNoteCode(int consignmentNoteCode) {
        this.consignmentNoteCode = consignmentNoteCode;
    }

    public int getPersonCode() {
        return personCode;
    }

    public void setPersonCode(int personCode) {
        this.personCode = personCode;
    }
}
