package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.PersonDTO;
import com.shakur.cafehelp.Service.PersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/persons")
public class PersonController {

    private final PersonService personService;

    @Autowired
    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    // Получение всех сотрудников
    @GetMapping
    public List<PersonDTO> getAllPersons() {
        return personService.findAll();
    }

    // Получение сотрудника по ID
    @GetMapping("/{id}")
    public PersonDTO getPersonById(@PathVariable int id) {
        return personService.getPersonById(id);
    }

    // Поиск по имени
    @GetMapping("/search")
    public List<PersonDTO> findByName(@RequestParam String name) {
        return personService.findByName(name);
    }

    // Создание нового сотрудника
    @PostMapping
    public PersonDTO createPerson(@RequestBody PersonDTO dto) {
        return personService.create(dto);
    }

    // Обновление сотрудника
    @PutMapping("/{id}")
    public PersonDTO updatePerson(@PathVariable int id, @RequestBody PersonDTO dto) {
        return personService.update(dto, dto); // dto в сервисе у тебя используется для personID и новых данных
    }

    // Удаление сотрудника
    @DeleteMapping("/{id}")
    public boolean deletePerson(@PathVariable int id) {
        return personService.deletePerson(id);
    }
}
