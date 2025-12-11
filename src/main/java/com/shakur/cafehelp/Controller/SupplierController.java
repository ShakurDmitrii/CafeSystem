package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.SupplierDTO;
import com.shakur.cafehelp.Service.SupplierService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/supplier")
@CrossOrigin(origins = "http://localhost:3000")

public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public List<SupplierDTO> getAllSuppliers() {
        return supplierService.getAllSuppliers();
    }
    @PostMapping
    public SupplierDTO addSupplier(@RequestBody SupplierDTO supplierDTO) {
        return supplierService.create(supplierDTO);
    }

    @GetMapping("/{id}")
    public SupplierDTO getSupplierById(@PathVariable int id) {
        return supplierService.getSupplierById(id);
    }
    @DeleteMapping("/{id}")
    public SupplierDTO deleteSupplierById(@PathVariable int id) {
        return supplierService.delete(id);
    }
}
