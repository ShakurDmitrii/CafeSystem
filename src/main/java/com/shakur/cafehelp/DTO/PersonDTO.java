package com.shakur.cafehelp.DTO;

import java.math.BigDecimal;

public class PersonDTO {
    public int personID;

    public String name;
    public BigDecimal salary;
    public BigDecimal numDays;
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

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }

    public BigDecimal getNumDays() {
        return numDays;
    }

    public void setNumDays(BigDecimal numDays) {
        this.numDays = numDays;
    }

    public BigDecimal getSalaryPerDay() {
        return salaryPerDay;
    }

    public void setSalaryPerDay(BigDecimal salaryPerDay) {
        this.salaryPerDay = salaryPerDay;
    }
}
