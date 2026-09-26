package com.financetracker.loan;

import com.financetracker.account.Account;
import com.financetracker.account.AccountService;
import com.financetracker.common.exception.BadRequestException;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.ResourceNotFoundException;
import com.financetracker.common.money.Money;
import com.financetracker.common.security.CurrentUserService;
import com.financetracker.loan.dto.LoanPaymentRequest;
import com.financetracker.loan.dto.LoanPaymentResponse;
import com.financetracker.loan.dto.LoanRequest;
import com.financetracker.loan.dto.LoanResponse;
import com.financetracker.loan.dto.PrepaymentRequest;
import com.financetracker.loan.dto.PrepaymentResponse;
import com.financetracker.loan.dto.ScheduleResponse;
import com.financetracker.transaction.AccountLedger;
import com.financetracker.transaction.Transaction;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionType;
import com.financetracker.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanService {

    private final LoanRepository loanRepository;
    private final LoanPaymentRepository paymentRepository;
    private final LoanCalculationService calculator;
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    public LoanService(
            LoanRepository loanRepository,
            LoanPaymentRepository paymentRepository,
            LoanCalculationService calculator,
            AccountService accountService,
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService
    ) {
        this.loanRepository = loanRepository;
        this.paymentRepository = paymentRepository;
        this.calculator = calculator;
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> list() {
        return loanRepository.findByUserIdOrderByNameAsc(currentUserService.requireId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LoanResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public LoanResponse create(LoanRequest request) {
        validateDates(request);
        User user = currentUserService.require();
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setPrepaymentStrategy(PrepaymentStrategy.REDUCE_TENURE);
        applyTerms(loan, request);
        loan.setOutstandingPrincipal(loan.getPrincipalAmount());
        return toResponse(loanRepository.save(loan));
    }

    @Transactional
    public LoanResponse update(Long id, LoanRequest request) {
        validateDates(request);
        Loan loan = require(id);
        if (paymentRepository.existsByLoanId(loan.getId())) {
            loan.setName(request.name().trim());
            loan.setLoanType(request.loanType());
            loan.setPaymentDueDay(request.paymentDueDay());
            return toResponse(loan);
        }
        applyTerms(loan, request);
        loan.setOutstandingPrincipal(loan.getPrincipalAmount());
        return toResponse(loan);
    }

    @Transactional
    public void delete(Long id) {
        Loan loan = require(id);
        List<LoanPayment> payments = paymentRepository.findByLoanIdOrderByPaymentDateAscIdAsc(loan.getId());
        payments.stream()
                .sorted(Comparator.comparing(LoanPayment::getId).reversed())
                .forEach(this::removePayment);
        loanRepository.delete(loan);
    }

    @Transactional(readOnly = true)
    public ScheduleResponse schedule(Long id) {
        Loan loan = require(id);
        List<LoanPayment> payments = paymentRepository.findByLoanIdOrderByPaymentDateAscIdAsc(loan.getId());
        List<ScheduleResponse.Row> rows = new ArrayList<>();
        int number = 1;
        for (LoanPayment payment : payments) {
            BigDecimal opening = Money.scale(payment.getRemainingPrincipal()
                    .add(payment.getPrincipalAmount())
                    .add(payment.getExtraPrincipalAmount()));
            rows.add(new ScheduleResponse.Row(
                    number++,
                    payment.getPaymentDate(),
                    opening,
                    Money.scale(payment.getTotalAmount()),
                    Money.scale(payment.getInterestAmount()),
                    Money.scale(payment.getPrincipalAmount()),
                    Money.scale(payment.getExtraPrincipalAmount()),
                    Money.scale(payment.getRemainingPrincipal()),
                    "ACTUAL"));
        }
        if (payments.isEmpty()) {
            for (ScheduleRow row : calculator.contractualSchedule(
                    loan.getPrincipalAmount(),
                    loan.getAnnualInterestRate(),
                    loan.getEmiAmount(),
                    loan.getFirstPaymentDate(),
                    loan.getTenureMonths())) {
                rows.add(toProjected(number++, row));
            }
        } else if (loan.getOutstandingPrincipal().signum() > 0) {
            LocalDate nextDate = payments.get(payments.size() - 1).getPaymentDate().plusMonths(1);
            for (ScheduleRow row : calculator.project(
                    loan.getOutstandingPrincipal(), loan.getAnnualInterestRate(), loan.getEmiAmount(), nextDate)) {
                rows.add(toProjected(number++, row));
            }
        }
        return new ScheduleResponse(loan.getId(), Money.scale(loan.getEmiAmount()), rows);
    }

    @Transactional(readOnly = true)
    public List<LoanPaymentResponse> payments(Long id) {
        Loan loan = require(id);
        return paymentRepository.findByLoanIdOrderByPaymentDateAscIdAsc(loan.getId()).stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    @Transactional
    public LoanPaymentResponse recordPayment(Long id, LoanPaymentRequest request) {
        Loan loan = require(id);
        BigDecimal extraPrincipal = request.extraPrincipalAmount() == null ? Money.ZERO : Money.scale(request.extraPrincipalAmount());
        PaymentSplit split = calculator.split(
                loan.getOutstandingPrincipal(),
                loan.getAnnualInterestRate(),
                loan.getEmiAmount(),
                extraPrincipal);
        LoanPayment payment = persistPayment(loan, request.paymentDate(), split, request.accountId(), request.notes());
        if (extraPrincipal.signum() > 0) {
            applyExtraPrincipalStrategy(loan, request, split);
            loanRepository.save(loan);
        }
        return toPaymentResponse(payment);
    }

    @Transactional
    public PrepaymentResponse prepay(Long id, PrepaymentRequest request) {
        Loan loan = require(id);
        PrepaymentStrategy strategy = request.strategy() == null ? PrepaymentStrategy.REDUCE_TENURE : request.strategy();
        BigDecimal extraPrincipal = Money.scale(request.extraPrincipalAmount());
        PrepaymentAnalysis analysis = calculator.analyze(
                loan.getOutstandingPrincipal(),
                loan.getAnnualInterestRate(),
                loan.getEmiAmount(),
                request.paymentDate(),
                extraPrincipal,
                strategy,
                strategy == PrepaymentStrategy.REDUCE_EMI ? request.remainingMonths() : null);
        LoanPayment payment = persistPayment(
                loan, request.paymentDate(), analysis.payment(), request.accountId(), request.notes());
        applyExtraPrincipalStrategy(loan, new LoanPaymentRequest(
                request.paymentDate(),
                extraPrincipal,
                strategy,
                request.remainingMonths(),
                request.accountId(),
                request.notes()), analysis.payment());
        loanRepository.save(loan);
        return new PrepaymentResponse(
                toPaymentResponse(payment),
                analysis.previousOutstanding(),
                analysis.newOutstanding(),
                analysis.interestSaved(),
                analysis.previousPayoffDate(),
                analysis.newPayoffDate(),
                analysis.emisReduced(),
                analysis.previousRemainingMonths(),
                analysis.newRemainingMonths(),
                analysis.strategy());
    }

    @Transactional
    public void deletePayment(Long loanId, Long paymentId) {
        Loan loan = require(loanId);
        LoanPayment payment = paymentRepository.findByIdAndLoanId(paymentId, loan.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan payment not found"));
        LoanPayment latest = paymentRepository.findByLoanIdOrderByPaymentDateAscIdAsc(loan.getId()).stream()
                .max(Comparator.comparing(LoanPayment::getId))
                .orElseThrow();
        if (!latest.getId().equals(payment.getId())) {
            throw new ConflictException("Only the latest loan payment can be deleted");
        }
        BigDecimal restored = payment.getRemainingPrincipal()
                .add(payment.getPrincipalAmount())
                .add(payment.getExtraPrincipalAmount());
        loan.setOutstandingPrincipal(Money.scale(restored));
        removePayment(payment);
    }

    private LoanPayment persistPayment(
            Loan loan,
            LocalDate paymentDate,
            PaymentSplit split,
            Long accountId,
            String notes
    ) {
        if (loan.getOutstandingPrincipal().signum() <= 0) {
            throw new ConflictException("Loan is already paid off");
        }
        LoanPayment payment = new LoanPayment();
        payment.setLoan(loan);
        payment.setPaymentDate(paymentDate);
        payment.setTotalAmount(split.total());
        payment.setPrincipalAmount(split.principal());
        payment.setInterestAmount(split.interest());
        payment.setExtraPrincipalAmount(split.extraPrincipal());
        payment.setRemainingPrincipal(split.remaining());
        payment.setNotes(blankToNull(notes));
        if (accountId != null) {
            Account account = accountService.lockOwned(loan.getUser().getId(), accountId);
            Transaction transaction = new Transaction();
            transaction.setUser(loan.getUser());
            transaction.setAccount(account);
            transaction.setType(TransactionType.LOAN_PAYMENT);
            transaction.setAmount(split.total());
            transaction.setTransactionDate(paymentDate);
            transaction.setDescription("Loan payment — " + loan.getName());
            transaction.setNotes(payment.getNotes());
            transaction.setLoanId(loan.getId());
            transactionRepository.save(transaction);
            AccountLedger.apply(TransactionType.LOAN_PAYMENT, split.total(), account, null);
            payment.setTransaction(transaction);
        }
        loan.setOutstandingPrincipal(split.remaining());
        return paymentRepository.save(payment);
    }

    private void removePayment(LoanPayment payment) {
        if (payment.getTransaction() != null) {
            Transaction transaction = payment.getTransaction();
            AccountLedger.reverse(transaction.getType(), transaction.getAmount(), transaction.getAccount(), null);
            payment.setTransaction(null);
            paymentRepository.save(payment);
            transactionRepository.delete(transaction);
        }
        paymentRepository.delete(payment);
    }

    private void applyTerms(Loan loan, LoanRequest request) {
        BigDecimal principal = Money.scale(request.principalAmount());
        BigDecimal rate = request.annualInterestRate().setScale(4, Money.ROUNDING);
        BigDecimal emi = calculator.emi(principal, rate, request.tenureMonths());
        loan.setName(request.name().trim());
        loan.setLoanType(request.loanType());
        loan.setPrincipalAmount(principal);
        loan.setAnnualInterestRate(rate);
        loan.setTenureMonths(request.tenureMonths());
        loan.setEmiAmount(emi);
        loan.setStartDate(request.startDate());
        loan.setFirstPaymentDate(request.firstPaymentDate());
        loan.setPaymentDueDay(request.paymentDueDay());
    }

    private void applyExtraPrincipalStrategy(Loan loan, LoanPaymentRequest request, PaymentSplit split) {
        PrepaymentStrategy strategy = request.strategy() == null ? PrepaymentStrategy.REDUCE_TENURE : request.strategy();
        switch (strategy) {
            case REDUCE_TENURE -> {
                List<ScheduleRow> projected = calculator.project(
                        split.remaining(),
                        loan.getAnnualInterestRate(),
                        loan.getEmiAmount(),
                        request.paymentDate().plusMonths(1));
                int remainingMonths = projected.size();
                loan.setPrepaymentStrategy(PrepaymentStrategy.REDUCE_TENURE);
                loan.setTenureMonths(Math.max(1, remainingMonths));
            }
            case REDUCE_EMI -> {
                Integer remainingMonths = request.remainingMonths();
                if (remainingMonths == null || remainingMonths < 1) {
                    throw new BadRequestException("Remaining months are required when reducing the EMI");
                }
                BigDecimal newEmi = calculator.emi(split.remaining(), loan.getAnnualInterestRate(), remainingMonths);
                loan.setPrepaymentStrategy(PrepaymentStrategy.REDUCE_EMI);
                loan.setTenureMonths(remainingMonths);
                loan.setEmiAmount(newEmi);
            }
            default -> throw new BadRequestException("Unsupported prepayment strategy");
        }
    }

    private void applyExtraPrincipalStrategy(Loan loan, LoanPaymentRequest request, PaymentSplit split) {
        PrepaymentStrategy strategy = request.strategy() == null ? PrepaymentStrategy.REDUCE_TENURE : request.strategy();
        switch (strategy) {
            case REDUCE_TENURE -> {
                List<ScheduleRow> projected = calculator.project(
                        split.remaining(),
                        loan.getAnnualInterestRate(),
                        loan.getEmiAmount(),
                        request.paymentDate().plusMonths(1));
                int remainingMonths = projected.size();
                loan.setPrepaymentStrategy(PrepaymentStrategy.REDUCE_TENURE);
                loan.setTenureMonths(Math.max(1, remainingMonths));
            }
            case REDUCE_EMI -> {
                Integer remainingMonths = request.remainingMonths();
                if (remainingMonths == null || remainingMonths < 1) {
                    throw new BadRequestException("Remaining months are required when reducing the EMI");
                }
                BigDecimal newEmi = calculator.emi(split.remaining(), loan.getAnnualInterestRate(), remainingMonths);
                loan.setPrepaymentStrategy(PrepaymentStrategy.REDUCE_EMI);
                loan.setTenureMonths(remainingMonths);
                loan.setEmiAmount(newEmi);
            }
            default -> throw new BadRequestException("Unsupported prepayment strategy");
        }
    }

    private void validateDates(LoanRequest request) {
        if (request.firstPaymentDate().isBefore(request.startDate())) {
            throw new BadRequestException("First payment date cannot be before the start date");
        }
        if (request.firstPaymentDate().getDayOfMonth() != request.paymentDueDay()) {
            throw new BadRequestException("First payment date must fall on the payment due day");
        }
    }

    private Loan require(Long id) {
        return loanRepository.findByIdAndUserId(id, currentUserService.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));
    }

    private LoanResponse toResponse(Loan loan) {
        LocalDate nextDue = paymentRepository.findByLoanIdOrderByPaymentDateAscIdAsc(loan.getId()).stream()
                .map(LoanPayment::getPaymentDate)
                .max(Comparator.naturalOrder())
                .map(date -> date.plusMonths(1))
                .orElse(loan.getFirstPaymentDate());
        List<ScheduleRow> remaining = loan.getOutstandingPrincipal().signum() == 0
                ? List.of()
                : calculator.project(
                        loan.getOutstandingPrincipal(),
                        loan.getAnnualInterestRate(),
                        loan.getEmiAmount(),
                        nextDue);
        LocalDate payoff = remaining.isEmpty() ? null : remaining.get(remaining.size() - 1).date();
        return new LoanResponse(
                loan.getId(),
                loan.getName(),
                loan.getLoanType(),
                Money.scale(loan.getPrincipalAmount()),
                Money.scale(loan.getOutstandingPrincipal()),
                loan.getAnnualInterestRate(),
                loan.getTenureMonths(),
                Money.scale(loan.getEmiAmount()),
                loan.getStartDate(),
                loan.getFirstPaymentDate(),
                loan.getPaymentDueDay(),
                loan.getPrepaymentStrategy(),
                remaining.size(),
                payoff,
                loan.getCreatedAt(),
                loan.getUpdatedAt());
    }

    private LoanPaymentResponse toPaymentResponse(LoanPayment payment) {
        Transaction transaction = payment.getTransaction();
        return new LoanPaymentResponse(
                payment.getId(),
                payment.getLoan().getId(),
                payment.getPaymentDate(),
                Money.scale(payment.getTotalAmount()),
                Money.scale(payment.getPrincipalAmount()),
                Money.scale(payment.getInterestAmount()),
                Money.scale(payment.getExtraPrincipalAmount()),
                Money.scale(payment.getRemainingPrincipal()),
                payment.getNotes(),
                transaction == null ? null : transaction.getId(),
                transaction == null ? null : transaction.getAccount().getId(),
                payment.getCreatedAt());
    }

    private ScheduleResponse.Row toProjected(int number, ScheduleRow row) {
        return new ScheduleResponse.Row(
                number,
                row.date(),
                row.openingPrincipal(),
                row.emi(),
                row.interest(),
                row.principal(),
                row.extraPrincipal(),
                row.closingPrincipal(),
                "PROJECTED");
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
