package com.glo.lending.loan.service;

import com.glo.lending.loan.components.LoanEventPublisher;
import com.glo.lending.loan.exception.LoanNotFoundException;
import com.glo.lending.loan.model.dto.CreateLoanRequest;
import com.glo.lending.loan.model.dto.RepaymentRequest;
import com.glo.lending.loan.model.enums.LoanState;
import com.glo.lending.loan.model.enums.LoanType;
import com.glo.lending.loan.repository.entities.Loan;
import com.glo.lending.loan.repository.entities.LoanRepayment;
import com.glo.lending.loan.repository.repo.*;
import com.glo.lending.loan.service.serviceImpl.LoanServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanService Unit Tests")
class LoanServiceTest {

    @Mock private LoanRepository loanRepository;
    @Mock private LoanInstallmentRepository installmentRepository;
    @Mock private LoanRepaymentRepository repaymentRepository;
    @Mock private LoanFeeRepository loanFeeRepository;
    @Mock private BillingCycleRepository billingCycleRepository;
    @Mock private LoanEventPublisher eventPublisher;
    @Mock private TransactionalOperator transactionalOperator;

    @InjectMocks private LoanServiceImpl loanService;

    private Loan loan;
    private UUID loanId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        loanId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        loan = new Loan();
        loan.setId(loanId);
        loan.setCustomerId(customerId);
        loan.setProductId(UUID.randomUUID());
        loan.setPrincipalAmount(BigDecimal.valueOf(10000));
        loan.setOutstandingBalance(BigDecimal.valueOf(10000));
        loan.setTotalFees(BigDecimal.ZERO);
        loan.setLoanType(LoanType.LUMP_SUM);
        loan.setState(LoanState.OPEN);
        loan.setOriginationDate(LocalDate.now());
        loan.setDueDate(LocalDate.now().plusDays(30));
        loan.setTenureValue(30);
        loan.setTenureType("DAYS");
        loan.setIdempotencyKey("IDEM-001");
        loan.setCreatedAt(LocalDateTime.now());
        loan.setUpdatedAt(LocalDateTime.now());

        // Configure TransactionalOperator to pass through Mono/Flux without modification
        // Using ArgumentMatchers.any() with proper casting to avoid type erasure issues
        lenient().when(transactionalOperator.transactional(ArgumentMatchers.<Mono>any()))
                .thenAnswer(inv -> inv.<Mono>getArgument(0));
    }

    @Nested
    @DisplayName("createLoan")
    class CreateLoan {

        @Test
        @DisplayName("should create lump sum loan and publish event")
        void createLoan_LumpSum_ReturnsLoanResponse() {
            // Given
            final CreateLoanRequest request = new CreateLoanRequest();
            request.setCustomerId(customerId);
            request.setProductId(UUID.randomUUID());
            request.setPrincipalAmount(BigDecimal.valueOf(10000));
            request.setLoanType(LoanType.LUMP_SUM);
            request.setTenureValue(30);
            request.setTenureType("DAYS");

            final Loan savedLoan = Loan.builder()
                    .id(loanId)
                    .customerId(customerId)
                    .productId(request.getProductId())
                    .principalAmount(request.getPrincipalAmount())
                    .outstandingBalance(request.getPrincipalAmount())
                    .totalFees(BigDecimal.ZERO)
                    .loanType(LoanType.LUMP_SUM)
                    .state(LoanState.OPEN)
                    .originationDate(LocalDate.now())
                    .dueDate(LocalDate.now().plusDays(30))
                    .tenureValue(30)
                    .tenureType("DAYS")
                    .idempotencyKey("IDEM-001")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(loanRepository.findByIdempotencyKey("IDEM-001")).thenReturn(Mono.empty());
            when(loanRepository.save(any(Loan.class))).thenReturn(Mono.just(savedLoan));
            when(eventPublisher.publishLoanEvent(any(), any())).thenReturn(Mono.empty());
            when(installmentRepository.findByLoanIdOrderByInstallmentNumber(loanId)).thenReturn(Flux.empty());

            // When & Then
            StepVerifier.create(loanService.createLoan(request, "IDEM-001"))
                    .assertNext(response -> {
                        assertEquals(loanId, response.id());
                        assertEquals(LoanType.LUMP_SUM, response.loanType());
                        assertTrue(response.installments().isEmpty());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return existing loan when idempotency key already exists")
        void createLoan_DuplicateIdempotencyKey_ReturnsExistingLoan() {
            // Given
            final CreateLoanRequest request = new CreateLoanRequest();
            request.setCustomerId(customerId);
            request.setProductId(UUID.randomUUID());
            request.setPrincipalAmount(BigDecimal.valueOf(10000));
            request.setLoanType(LoanType.LUMP_SUM);
            request.setTenureValue(30);
            request.setTenureType("DAYS");

            when(loanRepository.findByIdempotencyKey("IDEM-001")).thenReturn(Mono.just(loan));
            when(installmentRepository.findByLoanIdOrderByInstallmentNumber(loanId)).thenReturn(Flux.empty());

            // When & Then
            StepVerifier.create(loanService.createLoan(request, "IDEM-001"))
                    .assertNext(response -> assertEquals(loanId, response.id()))
                    .verifyComplete();

            verify(loanRepository, never()).save(any());
            verify(eventPublisher, never()).publishLoanEvent(any(), any());
        }
    }

    @Nested
    @DisplayName("getLoanById")
    class GetLoanById {

        @Test
        @DisplayName("should return loan with installments")
        void getLoanById_Exists_ReturnsLoan() {
            // Given
            when(loanRepository.findById(loanId)).thenReturn(Mono.just(loan));
            when(installmentRepository.findByLoanIdOrderByInstallmentNumber(loanId)).thenReturn(Flux.empty());

            // When & Then
            StepVerifier.create(loanService.getLoanById(loanId))
                    .assertNext(r -> assertEquals(loanId, r.id()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw when loan not found")
        void getLoanById_NotExists_ThrowsException() {
            // Given
            when(loanRepository.findById(loanId)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(loanService.getLoanById(loanId))
                    .expectError(LoanNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("getLoansByCustomerId")
    class GetLoansByCustomerId {

        @Test
        @DisplayName("should return all loans for customer")
        void getLoansByCustomerId_HasLoans_ReturnsFlux() {
            // Given
            when(loanRepository.findByCustomerId(customerId)).thenReturn(Flux.just(loan));
            when(installmentRepository.findByLoanIdOrderByInstallmentNumber(loanId)).thenReturn(Flux.empty());

            // When & Then
            StepVerifier.create(loanService.getLoansByCustomerId(customerId))
                    .assertNext(r -> assertEquals(customerId, r.customerId()))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("makeRepayment")
    class MakeRepayment {

        @Test
        @DisplayName("should process repayment and reduce balance")
        void makeRepayment_ValidAmount_ReducesBalance() {
            // Given
            final RepaymentRequest request = new RepaymentRequest();
            request.setLoanId(loanId);
            request.setAmount(BigDecimal.valueOf(5000));
            request.setPaymentReference("MPESA-001");

            final LoanRepayment repayment = new LoanRepayment();
            repayment.setId(UUID.randomUUID());
            repayment.setLoanId(loanId);
            repayment.setAmount(BigDecimal.valueOf(5000));
            repayment.setPaymentDate(LocalDateTime.now());
            repayment.setPaymentReference("MPESA-001");

            when(loanRepository.findById(loanId)).thenReturn(Mono.just(loan));
            when(repaymentRepository.save(any(LoanRepayment.class))).thenReturn(Mono.just(repayment));
            when(loanRepository.save(any(Loan.class))).thenReturn(Mono.just(loan));
            when(eventPublisher.publishLoanEvent(any(), any())).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(loanService.makeRepayment(request))
                    .assertNext(r -> {
                        assertEquals(BigDecimal.valueOf(5000), r.amount());
                        assertEquals("MPESA-001", r.paymentReference());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should close loan when fully repaid")
        void makeRepayment_FullRepayment_ClosesLoan() {
            // Given
            final RepaymentRequest request = new RepaymentRequest();
            request.setLoanId(loanId);
            request.setAmount(BigDecimal.valueOf(10000));
            request.setPaymentReference("MPESA-002");

            final LoanRepayment repayment = new LoanRepayment();
            repayment.setId(UUID.randomUUID());
            repayment.setLoanId(loanId);
            repayment.setAmount(BigDecimal.valueOf(10000));
            repayment.setPaymentDate(LocalDateTime.now());
            repayment.setPaymentReference("MPESA-002");

            when(loanRepository.findById(loanId)).thenReturn(Mono.just(loan));
            when(repaymentRepository.save(any(LoanRepayment.class))).thenReturn(Mono.just(repayment));
            when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> {
                final Loan saved = inv.getArgument(0);
                assertEquals(LoanState.CLOSED, saved.getState());
                return Mono.just(saved);
            });
            when(eventPublisher.publishLoanEvent(any(), any())).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(loanService.makeRepayment(request))
                    .assertNext(r -> assertNotNull(r.id()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should reject repayment on closed loan")
        void makeRepayment_ClosedLoan_ThrowsException() {
            // Given
            loan.setState(LoanState.CLOSED);
            final RepaymentRequest request = new RepaymentRequest();
            request.setLoanId(loanId);
            request.setAmount(BigDecimal.ONE);
            request.setPaymentReference("REF");
            when(loanRepository.findById(loanId)).thenReturn(Mono.just(loan));

            // When & Then
            StepVerifier.create(loanService.makeRepayment(request))
                    .expectErrorMatches(e -> e instanceof IllegalStateException && e.getMessage().contains("CLOSED"))
                    .verify();
        }

        @Test
        @DisplayName("should throw when loan not found")
        void makeRepayment_LoanNotFound_ThrowsException() {
            // Given
            final RepaymentRequest request = new RepaymentRequest();
            request.setLoanId(loanId);
            request.setAmount(BigDecimal.ONE);
            request.setPaymentReference("REF");
            when(loanRepository.findById(loanId)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(loanService.makeRepayment(request))
                    .expectError(LoanNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("cancelLoan")
    class CancelLoan {

        @Test
        @DisplayName("should cancel open loan")
        void cancelLoan_OpenLoan_CancelsSuccessfully() {
            // Given
            when(loanRepository.findById(loanId)).thenReturn(Mono.just(loan));
            when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(eventPublisher.publishLoanEvent(any(), any())).thenReturn(Mono.empty());
            when(installmentRepository.findByLoanIdOrderByInstallmentNumber(loanId)).thenReturn(Flux.empty());

            // When & Then
            StepVerifier.create(loanService.cancelLoan(loanId))
                    .assertNext(r -> assertEquals(LoanState.CANCELLED, r.state()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should reject cancellation of non-open loan")
        void cancelLoan_ClosedLoan_ThrowsException() {
            // Given
            loan.setState(LoanState.CLOSED);
            when(loanRepository.findById(loanId)).thenReturn(Mono.just(loan));

            // When & Then
            StepVerifier.create(loanService.cancelLoan(loanId))
                    .expectErrorMatches(e -> e instanceof IllegalStateException && e.getMessage().contains("OPEN"))
                    .verify();
        }

        @Test
        @DisplayName("should throw when loan not found during cancel")
        void cancelLoan_LoanNotFound_ThrowsException() {
            // Given
            when(loanRepository.findById(loanId)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(loanService.cancelLoan(loanId))
                    .expectError(LoanNotFoundException.class)
                    .verify();
        }
    }
}
