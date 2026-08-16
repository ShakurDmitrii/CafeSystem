package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class PersonDTO {
    public int personID;
    public String name;
    public BigDecimal salaryPerDay;

    public int getPersonID() {
        return personID;
    }

    public void setPersonID(int personID) {
        this.personID = personID;
    }



    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getSalaryPerDay() {
        return salaryPerDay;
    }

    public void setSalaryPerDay(BigDecimal salaryPerDay) {
        this.salaryPerDay = salaryPerDay;
    }
}
