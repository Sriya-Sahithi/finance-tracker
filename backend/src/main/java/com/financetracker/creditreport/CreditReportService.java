package com.financetracker.creditreport;

import com.financetracker.common.money.Money;
import com.financetracker.common.security.CurrentUserService;
import com.financetracker.creditreport.dto.CreditReportAccountDto;
import com.financetracker.creditreport.dto.CreditReportCommitRequest;
import com.financetracker.user.User;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditReportService {

    private final CreditReportAccountRepository repository;
    private final CurrentUserService currentUserService;

    public CreditReportService(CreditReportAccountRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<CreditReportAccountDto> list() {
        return repository.findByUserIdOrderByBankNameAsc(currentUserService.requireId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<CreditReportAccountDto> commit(CreditReportCommitRequest request) {
        User user = currentUserService.require();
        return request.accounts().stream()
                .map(dto -> toDto(repository.save(toEntity(dto, user, request.sourceFileName()))))
                .toList();
    }

    private CreditReportAccount toEntity(CreditReportAccountDto dto, User user, String sourceFileName) {
        CreditReportAccount entity = new CreditReportAccount();
        entity.setUser(user);
        entity.setBankName(dto.bankName().trim());
        entity.setAccountType(dto.accountType());
        entity.setAccountNumberMasked(mask(dto.accountNumberMasked()));
        entity.setCurrentBalance(Money.scale(dto.currentBalance()));
        entity.setCreditLimit(dto.creditLimit() != null ? Money.scale(dto.creditLimit()) : null);
        entity.setStatus(dto.status());
        entity.setReportDate(dto.reportDate());
        entity.setSourceFileName(sourceFileName);
        return entity;
    }

    /** Only the last 4 characters are kept in the clear (before encryption) as defense in depth. */
    private String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return null;
        }
        String trimmed = accountNumber.trim();
        if (trimmed.length() <= 4) {
            return trimmed;
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }

    private CreditReportAccountDto toDto(CreditReportAccount entity) {
        return new CreditReportAccountDto(
                entity.getId(),
                entity.getBankName(),
                entity.getAccountType(),
                entity.getAccountNumberMasked(),
                entity.getCurrentBalance(),
                entity.getCreditLimit(),
                entity.getStatus(),
                entity.getReportDate());
    }
}
