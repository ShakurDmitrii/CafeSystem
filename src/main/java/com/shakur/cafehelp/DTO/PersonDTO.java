package com.shakur.cafehelp.DTO;

public class PersonDTO {
    public int personID;

    public String name;
    public Double salary;
    public Double numDays;
    public Double salaryPerDay;

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

    public Double getSalary() {
        return salary;
    }

    public void setSalary(Double salary) {
        this.salary = salary;
    }

    public Double getNumDays() {
        return numDays;
    }

    public void setNumDays(Double numDays) {
        this.numDays = numDays;
    }

    public Double getSalaryPerDay() {
        return salaryPerDay;
    }

    public void setSalaryPerDay(Double salaryPerDay) {
        this.salaryPerDay = salaryPerDay;
    }
}
