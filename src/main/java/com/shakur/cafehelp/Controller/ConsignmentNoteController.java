package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.ConsignmentNoteDTO;
import com.shakur.cafehelp.Service.ConsignmentNoteService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/consignmentNote")
@CrossOrigin(origins = "http://localhost:3000")

public class ConsignmentNoteController {
    private final ConsignmentNoteService consignmentNoteService;
    public ConsignmentNoteController(ConsignmentNoteService consignmentNoteService) {
        this.consignmentNoteService = consignmentNoteService;
    }

    @GetMapping
    public List<ConsignmentNoteDTO> getAllConsignmentNotes() {
        return consignmentNoteService.getAllConsignmentNotes();
    }


    @GetMapping("/{id}")
    public ConsignmentNoteDTO getConsignmentNote(@PathVariable int id) {
        return consignmentNoteService.getConsignmentNoteById(id);

    }

    @GetMapping("/supplier/{id}")
    public ConsignmentNoteDTO getConsignmentNoteBySupplierId(@PathVariable int id) {
        return consignmentNoteService.getConsignmentNoteBySupplierId(id);
    }

    @PostMapping
    public ConsignmentNoteDTO createConsignmentNote(@RequestBody ConsignmentNoteDTO consignmentNoteDTO) {
        return consignmentNoteService.createConsignmentNote(consignmentNoteDTO);
    }

}
