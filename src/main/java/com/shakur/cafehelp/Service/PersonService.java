package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.PersonDTO;
import jooqdata.tables.records.PersonRecord;

import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Select;
import org.springframework.stereotype.Service;
import jooqdata.tables.Person;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PersonService {
    private static DSLContext dsl;
    public PersonService(DSLContext dsl) {this.dsl = dsl;}

    public List<PersonDTO> findAll() {
        return dsl.selectFrom(Person.PERSON)
                .fetch()
                .stream()
                .map(record -> {
                    PersonDTO dto = new PersonDTO();

                    dto.name = record.getName();
                    dto.personID = record.getPersonid();
                    dto.salary = record.getSalary();
                    dto.numDays = record.getNumdays();
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
                    dto.setNumDays(record.getNumdays());
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
                    dto.numDays = personRecord.getNumdays();
                    dto.salaryPerDay = personRecord.getSalaryperday();
                    return dto;
                }).toList();
    }

    public PersonDTO create(PersonDTO dto) {
        PersonRecord record =dsl.newRecord(jooqdata.tables.Person.PERSON);
        record.setName(dto.name);
        record.setPersonid(dto.personID);
        record.setSalary(dto.salary);
        record.setNumdays(dto.numDays);
        record.setSalaryperday(dto.salaryPerDay);
        record.store();
        return dto;
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
}
