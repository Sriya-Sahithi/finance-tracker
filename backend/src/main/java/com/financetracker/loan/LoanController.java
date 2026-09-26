package com.financetracker.loan;

import com.financetracker.common.exception.StandardApiErrors;
import com.financetracker.loan.dto.LoanPaymentRequest;
import com.financetracker.loan.dto.LoanPaymentResponse;
import com.financetracker.loan.dto.LoanRequest;
import com.financetracker.loan.dto.LoanResponse;
import com.financetracker.loan.dto.PrepaymentRequest;
import com.financetracker.loan.dto.PrepaymentResponse;
import com.financetracker.loan.dto.ScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
@Tag(name = "Loans")
@StandardApiErrors
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    @Operation(summary = "List loans with EMI and projected payoff")
    public List<LoanResponse> list() {
        return loanService.list();
    }

    @PostMapping
    @Operation(summary = "Create a loan and calculate its reducing-balance EMI")
    public ResponseEntity<LoanResponse> create(@Valid @RequestBody LoanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a loan")
    public LoanResponse get(@PathVariable Long id) {
        return loanService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a loan and recalibrate terms, outstanding principal, or remaining tenure")
    public LoanResponse update(@PathVariable Long id, @Valid @RequestBody LoanRequest request) {
        return loanService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a loan, its payments, and any linked account transactions")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        loanService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/schedule")
    @Operation(summary = "Amortization schedule. Recorded payments are actual; the rest is projected.")
    public ScheduleResponse schedule(@PathVariable Long id) {
        return loanService.schedule(id);
    }

    @GetMapping("/{id}/payments")
    @Operation(summary = "List recorded loan payments")
    public List<LoanPaymentResponse> payments(@PathVariable Long id) {
        return loanService.payments(id);
    }

    @PostMapping("/{id}/payments")
    @Operation(summary = "Record an EMI payment with optional extra principal")
    public ResponseEntity<LoanPaymentResponse> recordPayment(
            @PathVariable Long id,
            @Valid @RequestBody LoanPaymentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.recordPayment(id, request));
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    @Operation(summary = "Delete the latest loan payment and restore principal")
    public ResponseEntity<Void> deletePayment(@PathVariable Long id, @PathVariable Long paymentId) {
        loanService.deletePayment(id, paymentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/prepayment")
    @Operation(summary = "Apply extra principal, keep the EMI, and return interest saved and the new payoff date")
    public ResponseEntity<PrepaymentResponse> prepay(@PathVariable Long id, @Valid @RequestBody PrepaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.prepay(id, request));
    }
}
