package com.glo.lending.loan.utils;

import com.glo.lending.loan.model.dto.*;
import com.glo.lending.loan.repository.entities.*;

import java.util.List;

public final class LoanMapper {

    private LoanMapper() {
    }

    public static LoanResponse toResponse( Loan loan,  List<LoanInstallment> installments) {
        return new LoanResponse(loan.getId(), loan.getCustomerId(), loan.getProductId(),
                loan.getPrincipalAmount(), loan.getOutstandingBalance(), loan.getTotalFees(),
                loan.getLoanType(), loan.getState(), loan.getOriginationDate(), loan.getDueDate(),
                loan.getTenureValue(), loan.getTenureType(),
                installments.stream().map(LoanMapper::toInstallmentResponse).toList(),
                loan.getCreatedAt(), loan.getUpdatedAt());
    }

    public static InstallmentResponse toInstallmentResponse( LoanInstallment i) {
        return new InstallmentResponse(i.getId(), i.getInstallmentNumber(),
                i.getAmount(), i.getPaidAmount(), i.getDueDate(), i.getState(), i.getPaidAt());
    }

    public static RepaymentResponse toRepaymentResponse( LoanRepayment r) {
        return new RepaymentResponse(r.getId(), r.getLoanId(), r.getInstallmentId(),
                r.getAmount(), r.getPaymentDate(), r.getPaymentReference());
    }
}

