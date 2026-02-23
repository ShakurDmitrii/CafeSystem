package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.AuthLoginRequestDTO;
import com.shakur.cafehelp.DTO.AuthLoginResponseDTO;
import com.shakur.cafehelp.security.JwtService;
import jooqdata.tables.Person;
import jooqdata.tables.UserAccount;
import org.jooq.DSLContext;
import org.jooq.Record6;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final DSLContext dsl;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(DSLContext dsl, BCryptPasswordEncoder passwordEncoder, JwtService jwtService) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthLoginResponseDTO login(AuthLoginRequestDTO request) {
        if (request == null || isBlank(request.username) || isBlank(request.password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Логин и пароль обязательны");
        }

        UserAccount ua = UserAccount.USER_ACCOUNT;
        Person p = Person.PERSON;

        Record6<Integer, Integer, String, String, String, Boolean> account = dsl
                .select(ua.ID, ua.PERSONID, ua.USERNAME, ua.PASSWORD_HASH, ua.ROLE, ua.IS_ACTIVE)
                .from(ua)
                .where(ua.USERNAME.eq(request.username.trim()))
                .fetchOne();

        if (account == null || isBlank(account.value4()) || !passwordEncoder.matches(request.password, account.value4())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
        }

        if (!Boolean.TRUE.equals(account.value6())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Аккаунт отключен");
        }

        String personName = dsl.select(p.NAME)
                .from(p)
                .where(p.PERSONID.eq(account.value2()))
                .fetchOne(p.NAME);

        AuthLoginResponseDTO response = new AuthLoginResponseDTO();
        response.accountId = account.value1();
        response.personId = account.value2();
        response.username = account.value3();
        response.role = account.value5();
        response.personName = personName;
        response.tokenType = "Bearer";
        response.accessToken = jwtService.generateToken(
                response.username,
                response.accountId,
                response.personId,
                response.role
        );
        response.expiresInSeconds = jwtService.getJwtExpirationSeconds();
        return response;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
