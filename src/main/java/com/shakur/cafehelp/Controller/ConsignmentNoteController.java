package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.ConsignmentNoteDTO;
import com.shakur.cafehelp.Service.ConsignmentNoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/consignmentNote")
@CrossOrigin(origins = "http://localhost:3000")

public class ConsignmentNoteController {
    private final ConsignmentNoteService consignmentNoteService;
    public ConsignmentNoteController(ConsignmentNoteService consignmentNoteService) {
        this.consignmentNoteService = consignmentNoteService;
    }

    @PatchMapping("{id}")
    public ResponseEntity<Void> updateAmount(@PathVariable Integer id, @RequestBody Map<String, Double> body) {
        double amount = body.get("amount");
        consignmentNoteService.updateAmount(id, amount);
        return ResponseEntity.ok().build();
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
