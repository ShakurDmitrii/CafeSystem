package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.UserAccountCreateRequestDTO;
import com.shakur.cafehelp.DTO.UserAccountDTO;
import com.shakur.cafehelp.DTO.UserAccountPasswordUpdateDTO;
import com.shakur.cafehelp.DTO.UserAccountStatusUpdateDTO;
import jooqdata.tables.Person;
import jooqdata.tables.UserAccount;
import jooqdata.tables.records.UserAccountRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class
UserAccountService {
    private static final Set<String> ALLOWED_ROLES = Set.of("OWNER", "WORKER");

    private final DSLContext dsl;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserAccountService(DSLContext dsl, BCryptPasswordEncoder passwordEncoder) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserAccountDTO> getAll() {
        UserAccount ua = UserAccount.USER_ACCOUNT;
        Person p = Person.PERSON;

        return dsl.select(ua.ID, ua.PERSONID, p.NAME, ua.USERNAME, ua.ROLE, ua.IS_ACTIVE, ua.CREATED_AT)
                .from(ua)
                .join(p).on(ua.PERSONID.eq(p.PERSONID))
                .orderBy(ua.ID.asc())
                .fetch(this::mapDto);
    }

    public UserAccountDTO bootstrapOwner(UserAccountCreateRequestDTO request) {
        UserAccount ua = UserAccount.USER_ACCOUNT;
        Integer existing = dsl.selectCount().from(ua).fetchOne(0, Integer.class);
        if (existing != null && existing > 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bootstrap уже недоступен: аккаунты уже существуют");
        }

        if (request != null) {
            request.role = "OWNER";
            request.isActive = true;
        }
        return create(request);
    }

    public UserAccountDTO create(UserAccountCreateRequestDTO request) {
        if (request == null || request.personId == null || isBlank(request.username) || isBlank(request.password) || isBlank(request.role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personId, username, password, role обязательны");
        }

        String role = request.role.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Роль должна быть OWNER или WORKER");
        }

        Person p = Person.PERSON;
        UserAccount ua = UserAccount.USER_ACCOUNT;

        boolean personExists = dsl.fetchExists(
                dsl.selectOne().from(p).where(p.PERSONID.eq(request.personId))
        );
        if (!personExists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Сотрудник не найден");
        }

        String hash = passwordEncoder.encode(request.password);
        Boolean active = request.isActive != null ? request.isActive : Boolean.TRUE;

        try {
            UserAccountRecord rec = dsl.insertInto(ua)
                    .set(ua.PERSONID, request.personId)
                    .set(ua.USERNAME, request.username.trim())
                    .set(ua.PASSWORD_HASH, hash)
                    .set(ua.ROLE, role)
                    .set(ua.IS_ACTIVE, active)
                    .set(ua.CREATED_AT, LocalDateTime.now())
                    .returning()
                    .fetchOne();

            if (rec == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось создать аккаунт");
            }
            return getById(rec.getId());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Логин или сотрудник уже привязан к аккаунту");
        }
    }

    public UserAccountDTO updatePassword(Integer accountId, UserAccountPasswordUpdateDTO request) {
        if (accountId == null || accountId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный accountId");
        }
        if (request == null || isBlank(request.password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Новый пароль обязателен");
        }

        UserAccount ua = UserAccount.USER_ACCOUNT;
        int updated = dsl.update(ua)
                .set(ua.PASSWORD_HASH, passwordEncoder.encode(request.password))
                .where(ua.ID.eq(accountId))
                .execute();

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Аккаунт не найден");
        }
        return getById(accountId);
    }

    public UserAccountDTO updateStatus(Integer accountId, UserAccountStatusUpdateDTO request) {
        if (accountId == null || accountId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный accountId");
        }
        if (request == null || request.isActive == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "isActive обязателен");
        }

        UserAccount ua = UserAccount.USER_ACCOUNT;
        int updated = dsl.update(ua)
                .set(ua.IS_ACTIVE, request.isActive)
                .where(ua.ID.eq(accountId))
                .execute();

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Аккаунт не найден");
        }
        return getById(accountId);
    }

    private UserAccountDTO getById(Integer accountId) {
        UserAccount ua = UserAccount.USER_ACCOUNT;
        Person p = Person.PERSON;
        Record record = dsl.select(ua.ID, ua.PERSONID, p.NAME, ua.USERNAME, ua.ROLE, ua.IS_ACTIVE, ua.CREATED_AT)
                .from(ua)
                .join(p).on(ua.PERSONID.eq(p.PERSONID))
                .where(ua.ID.eq(accountId))
                .fetchOne();

        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Аккаунт не найден");
        }
        return mapDto(record);
    }

    private UserAccountDTO mapDto(Record r) {
        UserAccount ua = UserAccount.USER_ACCOUNT;
        Person p = Person.PERSON;
        UserAccountDTO dto = new UserAccountDTO();
        dto.id = r.get(ua.ID);
        dto.personId = r.get(ua.PERSONID);
        dto.personName = r.get(p.NAME);
        dto.username = r.get(ua.USERNAME);
        dto.role = r.get(ua.ROLE);
        dto.isActive = Boolean.TRUE.equals(r.get(ua.IS_ACTIVE));
        dto.createdAt = r.get(ua.CREATED_AT);
        return dto;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
