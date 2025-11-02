package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.PersonDTO;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import jooq.jooq
import java.util.List;

@Service
public class PersonService {
    private static DSLContext dsl;
    public PersonService(DSLContext dsl) {this.dsl = dsl;}

    public List<PersonDTO> findAll() {
        return dsl.selectFrom(PersonDTO.PERSON)
                .fetch()
                .stream()
                .map(record -> {
                    PersonDTO dto = new PersonDTO();

                    dto.name = record.getName();
                    dto.personID = record.getPersonId();
                    dto.salary = record.getSalary();
                    dto.numDays = record.getNumDays();
                    dto.salaryPerDay = record.getSalaryPerDays();
                    return dto;
                })
                .toList();
    }
}
