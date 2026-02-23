package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.AuthLoginRequestDTO;
import com.shakur.cafehelp.DTO.AuthLoginResponseDTO;
import com.shakur.cafehelp.DTO.UserAccountCreateRequestDTO;
import com.shakur.cafehelp.DTO.UserAccountDTO;
import com.shakur.cafehelp.Service.AuthService;
import com.shakur.cafehelp.Service.UserAccountService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserAccountService userAccountService;

    public AuthController(AuthService authService, UserAccountService userAccountService) {
        this.authService = authService;
        this.userAccountService = userAccountService;
    }

    @PostMapping("/login")
    public AuthLoginResponseDTO login(@RequestBody AuthLoginRequestDTO request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        return Map.of(
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities()
        );
    }

    @PostMapping("/bootstrap")
    public UserAccountDTO bootstrap(@RequestBody UserAccountCreateRequestDTO request) {
        return userAccountService.bootstrapOwner(request);
    }
}
