package com.financetracker.account;

import com.financetracker.account.dto.AccountDetailResponse;
import com.financetracker.account.dto.AccountRequest;
import com.financetracker.account.dto.AccountResponse;
import com.financetracker.common.exception.BadRequestException;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.ResourceNotFoundException;
import com.financetracker.common.money.Money;
import com.financetracker.common.security.CurrentUserService;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.dto.TransactionResponse;
import com.financetracker.user.User;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final String SUPPORTED_CURRENCY = "INR";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    public AccountService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list() {
        return accountRepository.findByUserIdOrderByNameAsc(currentUserService.requireId()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountDetailResponse get(Long id) {
        Account account = require(id);
        List<TransactionResponse> recent = transactionRepository
                .findRecentForAccount(account.getUser().getId(), account.getId(), PageRequest.of(0, 10))
                .stream()
                .map(TransactionResponse::from)
                .toList();
        return new AccountDetailResponse(AccountResponse.from(account), recent);
    }

    @Transactional
    public AccountResponse create(AccountRequest request) {
        User user = currentUserService.require();
        Account account = new Account();
        account.setUser(user);
        account.setName(request.name().trim());
        account.setType(request.type());
        account.setOpeningBalance(Money.scale(request.openingBalance()));
        account.setCurrentBalance(Money.scale(request.openingBalance()));
        account.setCurrency(normalizeCurrency(request.currency()));
        account.setAccountNumber(normalizeAccountNumber(request.accountNumber()));
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse update(Long id, AccountRequest request) {
        Account account = require(id);
        var opening = Money.scale(request.openingBalance());
        var delta = opening.subtract(account.getOpeningBalance());
        account.setName(request.name().trim());
        account.setType(request.type());
        account.setOpeningBalance(opening);
        account.setCurrentBalance(Money.scale(account.getCurrentBalance().add(delta)));
        account.setCurrency(normalizeCurrency(request.currency()));
        account.setAccountNumber(normalizeAccountNumber(request.accountNumber()));
        return AccountResponse.from(account);
    }

    @Transactional
    public void delete(Long id) {
        Account account = require(id);
        if (transactionRepository.existsForAccount(account.getId())) {
            throw new ConflictException("Account has transactions and cannot be deleted");
        }
        accountRepository.delete(account);
    }

    public Account lockOwned(Long userId, Long accountId) {
        return accountRepository.lockByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    @Transactional(readOnly = true)
    public List<Account> findOwned(Long userId) {
        return accountRepository.findByUserIdOrderByNameAsc(userId);
    }

    private Account require(Long id) {
        return accountRepository.findByIdAndUserId(id, currentUserService.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private String normalizeCurrency(String currency) {
        String code = (currency == null || currency.isBlank()) ? SUPPORTED_CURRENCY : currency.trim().toUpperCase();
        if (!SUPPORTED_CURRENCY.equals(code)) {
            throw new BadRequestException("Only INR is supported currently");
        }
        return code;
    }

    private String normalizeAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return null;
        }
        return accountNumber.trim();
    }
}
