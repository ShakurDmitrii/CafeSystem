package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.UserAccountCreateRequestDTO;
import com.shakur.cafehelp.DTO.UserAccountDTO;
import com.shakur.cafehelp.DTO.UserAccountPasswordUpdateDTO;
import com.shakur.cafehelp.DTO.UserAccountStatusUpdateDTO;
import com.shakur.cafehelp.Service.UserAccountService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user-accounts")
public class UserAccountController {

    private final UserAccountService userAccountService;

    public UserAccountController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping
    public List<UserAccountDTO> getAll() {
        return userAccountService.getAll();
    }

    @PostMapping
    public UserAccountDTO create(@RequestBody UserAccountCreateRequestDTO request) {
        return userAccountService.create(request);
    }

    @PatchMapping("/{id}/password")
    public UserAccountDTO updatePassword(@PathVariable Integer id, @RequestBody UserAccountPasswordUpdateDTO request) {
        return userAccountService.updatePassword(id, request);
    }

    @PatchMapping("/{id}/status")
    public UserAccountDTO updateStatus(@PathVariable Integer id, @RequestBody UserAccountStatusUpdateDTO request) {
        return userAccountService.updateStatus(id, request);
    }
}

