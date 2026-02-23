package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.PersonDTO;
import com.shakur.cafehelp.DTO.PersonRegistrationRequestDTO;
import jooqdata.tables.UserAccount;
import jooqdata.tables.records.PersonRecord;

import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Select;
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
    private static DSLContext dsl;
    private final BCryptPasswordEncoder passwordEncoder;
    public PersonService(DSLContext dsl, BCryptPasswordEncoder passwordEncoder) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
    }

    public List<PersonDTO> findAll() {
        return dsl.selectFrom(Person.PERSON)
                .fetch()
                .stream()
                .map(record -> {
                    PersonDTO dto = new PersonDTO();

                    dto.name = record.getName();
                    dto.personID = record.getPersonid();
                    dto.salary = record.getSalary();
                    dto.numDays = record.getNumdays() != null ? record.getNumdays() : 0;
                    dto.salaryPerDay = record.getSalaryperday();
                    return dto;
                })
                .toList();
    }

    public PersonDTO getPersonById(int id) {
        return dsl.selectFrom(Person.PERSON)
                .where(Person.PERSON.PERSONID.eq(id))
                .fetchOne(record -> {
                    PersonDTO dto = new PersonDTO();
                    dto.setPersonID(record.getPersonid());
                    dto.setName(record.getName());
                    dto.setSalary(record.getSalary());
                    dto.setNumDays(record.getNumdays() != null ? record.getNumdays() : 0);
                    dto.setSalaryPerDay(record.getSalaryperday());
                    return dto;
                });
    }

    public List<PersonDTO> findByName(String name) {
        return dsl.selectFrom(Person.PERSON)
                .where(Person.PERSON.NAME.eq(name))
                .fetch()
                .stream()
                .map(personRecord -> {
                    PersonDTO dto = new PersonDTO();
                    dto.name = personRecord.getName();
                    dto.personID = personRecord.getPersonid();
                    dto.salary = personRecord.getSalary();
                    dto.numDays = personRecord.getNumdays() != null ? personRecord.getNumdays() : 0;
                    dto.salaryPerDay = personRecord.getSalaryperday();
                    return dto;
                }).toList();
    }

    public PersonDTO create(PersonDTO dto) {
        PersonRecord record =dsl.newRecord(jooqdata.tables.Person.PERSON);
        record.setName(dto.name);
        record.setSalary(dto.salary != null ? dto.salary : BigDecimal.ZERO);
        record.setNumdays(dto.numDays);
        record.setSalaryperday(dto.salaryPerDay != null ? dto.salaryPerDay : BigDecimal.ZERO);
        record.store();
        dto.personID = record.getPersonid();
        return dto;
    }

    @Transactional
    public PersonDTO register(PersonRegistrationRequestDTO dto) {
        if (dto == null || isBlank(dto.name) || isBlank(dto.username) || isBlank(dto.password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name, username, password обязательны");
        }

        String role = isBlank(dto.role) ? "WORKER" : dto.role.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Роль должна быть OWNER или WORKER");
        }

        PersonRecord personRecord = dsl.newRecord(Person.PERSON);
        personRecord.setName(dto.name.trim());
        personRecord.setSalary(dto.salary != null ? dto.salary : BigDecimal.ZERO);
        personRecord.setNumdays(dto.numDays != null ? dto.numDays : 0);
        personRecord.setSalaryperday(dto.salaryPerDay != null ? dto.salaryPerDay : BigDecimal.ZERO);
        personRecord.store();

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
        response.salary = personRecord.getSalary();
        response.numDays = personRecord.getNumdays() != null ? personRecord.getNumdays() : 0;
        response.salaryPerDay = personRecord.getSalaryperday();
        return response;
    }
    public PersonDTO update(PersonDTO personId, PersonDTO dto) {
    PersonRecord record =dsl.fetchOne(jooqdata.tables.Person.PERSON,
    jooqdata.tables.Person.PERSON.PERSONID.eq((Select<? extends Record1<Integer>>) personId));
    if (record == null) {
        throw new RuntimeException("No record found for id " + personId);
    }
    record.setName(dto.name);
    record.setPersonid(dto.personID);
    record.setSalary(dto.salary);
    record.setNumdays(dto.numDays);
    record.setSalaryperday(dto.salaryPerDay);
    record.store();
    return dto;
    }


    // Удаление сотрудника
    public boolean deletePerson(int id) {
        return dsl.deleteFrom(Person.PERSON)
                .where(Person.PERSON.PERSONID.eq(id))
                .execute() > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
