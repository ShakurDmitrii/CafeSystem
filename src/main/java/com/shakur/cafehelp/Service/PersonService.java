package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.PersonDTO;
import com.shakur.cafehelp.DTO.PersonRegistrationRequestDTO;
import jooqdata.tables.UserAccount;
import jooqdata.tables.records.PersonRecord;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jooqdata.tables.Person;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class PersonService {
    private static final Set<String> ALLOWED_ROLES = Set.of("OWNER", "WORKER");
    private static final Field<Boolean> PERSON_ARCHIVED = DSL.field(DSL.name("archived"), Boolean.class);
    private static DSLContext dsl;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PayrollService payrollService;
    public PersonService(DSLContext dsl, BCryptPasswordEncoder passwordEncoder, PayrollService payrollService) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
        this.payrollService = payrollService;
    }

    public List<PersonDTO> findAll() {
        return dsl.selectFrom(Person.PERSON)
                .where(PERSON_ARCHIVED.eq(false))
                .fetch()
                .stream()
                .map(record -> {
                    PersonDTO dto = new PersonDTO();

                    dto.name = record.getName();
                    dto.personID = record.getPersonid();
                    dto.salaryPerDay = record.getSalaryperday();
                    return dto;
                })
                .toList();
    }

    public PersonDTO getPersonById(int id) {
        return dsl.selectFrom(Person.PERSON)
                .where(Person.PERSON.PERSONID.eq(id))
                .and(PERSON_ARCHIVED.eq(false))
                .fetchOne(record -> {
                    PersonDTO dto = new PersonDTO();
                    dto.setPersonID(record.getPersonid());
                    dto.setName(record.getName());
                    dto.setSalaryPerDay(record.getSalaryperday());
                    return dto;
                });
    }

    public List<PersonDTO> findByName(String name) {
        return dsl.selectFrom(Person.PERSON)
                .where(Person.PERSON.NAME.eq(name))
                .and(PERSON_ARCHIVED.eq(false))
                .fetch()
                .stream()
                .map(personRecord -> {
                    PersonDTO dto = new PersonDTO();
                    dto.name = personRecord.getName();
                    dto.personID = personRecord.getPersonid();
                    dto.salaryPerDay = personRecord.getSalaryperday();
                    return dto;
                }).toList();
    }

    public PersonDTO create(PersonDTO dto) {
        if (dto == null || isBlank(dto.name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Имя сотрудника обязательно");
        }
        if (isNegative(dto.salaryPerDay)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ставка за смену не может быть отрицательной");
        }
        PersonRecord record =dsl.newRecord(jooqdata.tables.Person.PERSON);
        record.setName(dto.name.trim());
        record.setSalaryperday(dto.salaryPerDay != null ? dto.salaryPerDay : BigDecimal.ZERO);
        record.store();
        dsl.update(Person.PERSON)
                .set(PERSON_ARCHIVED, false)
                .where(Person.PERSON.PERSONID.eq(record.getPersonid()))
                .execute();
        dto.personID = record.getPersonid();
        return dto;
    }

    @Transactional
    public PersonDTO register(PersonRegistrationRequestDTO dto) {
        if (dto == null || isBlank(dto.name) || isBlank(dto.username) || isBlank(dto.password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name, username, password обязательны");
        }
        if (isNegative(dto.salaryPerDay)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ставка за смену не может быть отрицательной");
        }

        String role = isBlank(dto.role) ? "WORKER" : dto.role.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Роль должна быть OWNER или WORKER");
        }

        PersonRecord personRecord = dsl.newRecord(Person.PERSON);
        personRecord.setName(dto.name.trim());
        personRecord.setSalaryperday(dto.salaryPerDay != null ? dto.salaryPerDay : BigDecimal.ZERO);
        personRecord.store();
        dsl.update(Person.PERSON)
                .set(PERSON_ARCHIVED, false)
                .where(Person.PERSON.PERSONID.eq(personRecord.getPersonid()))
                .execute();

        Integer personId = personRecord.getPersonid();
        if (personId == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось создать сотрудника");
        }

        try {
            dsl.insertInto(UserAccount.USER_ACCOUNT)
                    .set(UserAccount.USER_ACCOUNT.PERSONID, personId)
                    .set(UserAccount.USER_ACCOUNT.USERNAME, dto.username.trim())
                    .set(UserAccount.USER_ACCOUNT.PASSWORD_HASH, passwordEncoder.encode(dto.password))
                    .set(UserAccount.USER_ACCOUNT.ROLE, role)
                    .set(UserAccount.USER_ACCOUNT.IS_ACTIVE, dto.isActive == null || dto.isActive)
                    .execute();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Не удалось создать аккаунт: логин уже занят");
        }

        PersonDTO response = new PersonDTO();
        response.personID = personId;
        response.name = personRecord.getName();
        response.salaryPerDay = personRecord.getSalaryperday();
        return response;
    }
    @Transactional
    public PersonDTO update(int personId, PersonDTO dto) {
        if (dto == null || isBlank(dto.name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Имя сотрудника обязательно");
        }
        if (isNegative(dto.salaryPerDay)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Зарплата не может быть отрицательной");
        }

        PersonRecord record = dsl.selectFrom(Person.PERSON)
                .where(Person.PERSON.PERSONID.eq(personId))
                .and(PERSON_ARCHIVED.eq(false))
                .forUpdate()
                .fetchOne();
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Сотрудник не найден");
        }

        record.setName(dto.name.trim());
        record.setSalaryperday(dto.salaryPerDay != null ? dto.salaryPerDay : BigDecimal.ZERO);
        record.store();

        PersonDTO response = new PersonDTO();
        response.personID = record.getPersonid();
        response.name = record.getName();
        response.salaryPerDay = record.getSalaryperday();
        return response;
    }


    // Удаление сотрудника
    @Transactional
    public boolean deletePerson(int id) {
        PersonRecord person = dsl.selectFrom(Person.PERSON)
                .where(Person.PERSON.PERSONID.eq(id))
                .and(PERSON_ARCHIVED.eq(false))
                .forUpdate()
                .fetchOne();
        if (person == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Сотрудник не найден");
        }
        boolean hasOpenShift = dsl.fetchExists(
                dsl.selectOne()
                        .from(jooqdata.tables.Shift.SHIFT)
                        .where(jooqdata.tables.Shift.SHIFT.ENDTIME.isNull())
                        .and(
                                jooqdata.tables.Shift.SHIFT.PERSONCODE.eq(id)
                                        .orExists(
                                                dsl.selectOne()
                                                        .from(jooqdata.tables.Shiftperson.SHIFTPERSON)
                                                        .where(jooqdata.tables.Shiftperson.SHIFTPERSON.SHIFTID
                                                                .eq(jooqdata.tables.Shift.SHIFT.ID))
                                                        .and(jooqdata.tables.Shiftperson.SHIFTPERSON.PERSONID.eq(id))
                                        )
                        )
        );
        if (hasOpenShift) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Нельзя архивировать сотрудника, пока у него есть открытая смена"
            );
        }
        if (payrollService.getOutstandingBalance(id).signum() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Нельзя архивировать сотрудника, пока ему не выплачен остаток зарплаты"
            );
        }

        dsl.update(UserAccount.USER_ACCOUNT)
                .set(UserAccount.USER_ACCOUNT.IS_ACTIVE, false)
                .where(UserAccount.USER_ACCOUNT.PERSONID.eq(id))
                .execute();

        return dsl.update(Person.PERSON)
                .set(PERSON_ARCHIVED, true)
                .where(Person.PERSON.PERSONID.eq(id))
                .execute() > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }
}
