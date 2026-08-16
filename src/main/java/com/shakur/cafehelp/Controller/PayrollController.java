package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.SalaryPaymentDTO;
import com.shakur.cafehelp.DTO.SalaryPaymentPageDTO;
import com.shakur.cafehelp.DTO.SalaryPaymentRequestDTO;
import com.shakur.cafehelp.DTO.SalaryReversalRequestDTO;
import com.shakur.cafehelp.DTO.SalarySummaryDTO;
import com.shakur.cafehelp.Service.PayrollService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollController {
    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @GetMapping("/employees")
    public List<SalarySummaryDTO> getEmployeeSummaries() {
        return payrollService.getSummaries();
    }

    @GetMapping("/employees/{personId}/payments")
    public SalaryPaymentPageDTO getPaymentHistory(
            @PathVariable int personId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return payrollService.getPaymentHistory(personId, page, size);
    }

    @PostMapping("/employees/{personId}/payments")
    public ResponseEntity<SalaryPaymentDTO> createPayment(
            @PathVariable int personId,
            @RequestBody SalaryPaymentRequestDTO request,
            Authentication authentication
    ) {
        SalaryPaymentDTO payment = payrollService.createPayment(personId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @PostMapping("/payments/{paymentId}/reversals")
    public ResponseEntity<SalaryPaymentDTO> reversePayment(
            @PathVariable long paymentId,
            @RequestBody SalaryReversalRequestDTO request,
            Authentication authentication
    ) {
        SalaryPaymentDTO reversal = payrollService.reversePayment(paymentId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(reversal);
    }
}
