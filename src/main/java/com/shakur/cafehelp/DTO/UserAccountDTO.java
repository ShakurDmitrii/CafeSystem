package com.shakur.cafehelp.DTO;

import java.time.LocalDateTime;

public class UserAccountDTO {
    public int id;
    public int personId;
    public String personName;
    public String username;
    public String role;
    public boolean isActive;
    public LocalDateTime createdAt;
}

