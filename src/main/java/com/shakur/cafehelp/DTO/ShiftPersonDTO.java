package com.shakur.cafehelp.DTO;

public class ShiftPersonDTO {
    public int shiftPersonID;
    public int shiftId;
    public int personId;

    public int getShiftPersonID() {
        return shiftPersonID;
    }

    public void setShiftPersonID(int shiftPersonID) {
        this.shiftPersonID = shiftPersonID;
    }

    public int getShiftId() {
        return shiftId;
    }

    public void setShiftId(int shiftId) {
        this.shiftId = shiftId;
    }

    public int getPersonId() {
        return personId;
    }

    public void setPersonId(int personId) {
        this.personId = personId;
    }
}
