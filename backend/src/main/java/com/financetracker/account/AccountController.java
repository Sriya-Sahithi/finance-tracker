package com.financetracker.account;

import com.financetracker.account.dto.AccountDetailResponse;
import com.financetracker.account.dto.AccountRequest;
import com.financetracker.account.dto.AccountResponse;
import com.financetracker.common.exception.StandardApiErrors;
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
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")
@StandardApiErrors
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "List accounts and current balances")
    public List<AccountResponse> list() {
        return accountService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account and its recent transactions")
    public AccountDetailResponse get(@PathVariable Long id) {
        return accountService.get(id);
    }

    @PostMapping
    @Operation(summary = "Create an account")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an account")
    public AccountResponse update(@PathVariable Long id, @Valid @RequestBody AccountRequest request) {
        return accountService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an account that has no transactions")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
