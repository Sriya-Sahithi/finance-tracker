package com.financetracker.transaction;

import com.financetracker.account.Account;
import com.financetracker.account.AccountService;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryService;
import com.financetracker.category.CategoryType;
import com.financetracker.common.exception.BadRequestException;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.ResourceNotFoundException;
import com.financetracker.common.money.Money;
import com.financetracker.common.security.CurrentUserService;
import com.financetracker.common.web.PageResponse;
import com.financetracker.transaction.dto.TransactionRequest;
import com.financetracker.transaction.dto.TransactionResponse;
import com.financetracker.user.User;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final CurrentUserService currentUserService;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountService accountService,
            CategoryService categoryService,
            CurrentUserService currentUserService
    ) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(
            int page,
            int size,
            String search,
            Long categoryId,
            Long accountId,
            LocalDate from,
            LocalDate to,
            TransactionType type
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        User user = currentUserService.require();
        var result = transactionRepository.findAll(
                TransactionSpecifications.filter(user.getId(), type, accountId, categoryId, from, to, search),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("id"))));
        return PageResponse.from(result.map(TransactionResponse::from));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long id) {
        return TransactionResponse.from(require(id));
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        User user = currentUserService.require();
        Resolved resolved = resolve(user.getId(), request);
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        applyResolved(transaction, request, resolved);
        AccountLedger.apply(transaction.getType(), transaction.getAmount(), resolved.account(), resolved.transferAccount());
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        User user = currentUserService.require();
        Transaction transaction = require(id);
        rejectLinkedLoanTransaction(transaction);
        lockAccounts(user.getId(), transaction.getAccount().getId(),
                transaction.getTransferAccount() == null ? null : transaction.getTransferAccount().getId());
        AccountLedger.reverse(
                transaction.getType(),
                transaction.getAmount(),
                transaction.getAccount(),
                transaction.getTransferAccount());
        Resolved resolved = resolve(user.getId(), request);
        applyResolved(transaction, request, resolved);
        AccountLedger.apply(transaction.getType(), transaction.getAmount(), resolved.account(), resolved.transferAccount());
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public void delete(Long id) {
        Transaction transaction = require(id);
        rejectLinkedLoanTransaction(transaction);
        lockAccounts(transaction.getUser().getId(), transaction.getAccount().getId(),
                transaction.getTransferAccount() == null ? null : transaction.getTransferAccount().getId());
        AccountLedger.reverse(
                transaction.getType(),
                transaction.getAmount(),
                transaction.getAccount(),
                transaction.getTransferAccount());
        transactionRepository.delete(transaction);
    }

    private void rejectLinkedLoanTransaction(Transaction transaction) {
        if (transaction.getLoanId() != null) {
            throw new ConflictException("This transaction belongs to a loan payment. Change it from the loan instead.");
        }
    }

    private Transaction require(Long id) {
        return transactionRepository.findByIdAndUserId(id, currentUserService.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    private void applyResolved(Transaction transaction, TransactionRequest request, Resolved resolved) {
        transaction.setAccount(resolved.account());
        transaction.setTransferAccount(resolved.transferAccount());
        transaction.setCategory(resolved.category());
        transaction.setType(request.type());
        transaction.setAmount(Money.scale(request.amount()));
        transaction.setTransactionDate(request.transactionDate());
        transaction.setDescription(blankToNull(request.description()));
        transaction.setNotes(blankToNull(request.notes()));
    }

    private Resolved resolve(Long userId, TransactionRequest request) {
        if (request.amount().compareTo(Money.ZERO) <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        Account account;
        Account transfer = null;
        if (request.type() == TransactionType.TRANSFER) {
            if (request.transferAccountId() == null) {
                throw new BadRequestException("Transfers require a destination account");
            }
            if (request.transferAccountId().equals(request.accountId())) {
                throw new BadRequestException("Transfer accounts must be different");
            }
            LockedPair locked = lockAccounts(userId, request.accountId(), request.transferAccountId());
            account = locked.firstId().equals(request.accountId()) ? locked.first() : locked.second();
            transfer = locked.firstId().equals(request.transferAccountId()) ? locked.first() : locked.second();
        } else {
            if (request.transferAccountId() != null) {
                throw new BadRequestException("Only transfers have a destination account");
            }
            account = accountService.lockOwned(userId, request.accountId());
        }

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryService.requireOwned(request.categoryId(), userId);
        }
        switch (request.type()) {
            case INCOME -> requireCategory(category, CategoryType.INCOME);
            case EXPENSE -> requireCategory(category, CategoryType.EXPENSE);
            case TRANSFER -> {
                if (category != null) {
                    throw new BadRequestException("Transfers do not use a category");
                }
            }
            case LOAN_PAYMENT -> {
                if (category != null && category.getType() != CategoryType.EXPENSE) {
                    throw new BadRequestException("Loan payments can only use an expense category");
                }
            }
        }
        return new Resolved(account, transfer, category);
    }

    private LockedPair lockAccounts(Long userId, Long accountId, Long transferAccountId) {
        if (transferAccountId == null) {
            return new LockedPair(accountId, accountService.lockOwned(userId, accountId), null, null);
        }
        long firstId = Math.min(accountId, transferAccountId);
        long secondId = Math.max(accountId, transferAccountId);
        return new LockedPair(
                firstId,
                accountService.lockOwned(userId, firstId),
                secondId,
                accountService.lockOwned(userId, secondId));
    }

    private void requireCategory(Category category, CategoryType expected) {
        if (category == null || category.getType() != expected) {
            throw new BadRequestException("A " + expected.name().toLowerCase() + " category is required");
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record Resolved(Account account, Account transferAccount, Category category) {
    }

    private record LockedPair(Long firstId, Account first, Long secondId, Account second) {
    }
}
