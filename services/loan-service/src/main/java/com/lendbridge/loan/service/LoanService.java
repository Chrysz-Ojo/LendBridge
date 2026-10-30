package com.lendbridge.loan.service;

import com.lendbridge.loan.dto.AgreementResponse;
import com.lendbridge.loan.dto.CreateLoanRequest;
import com.lendbridge.loan.dto.LoanResponse;
import com.lendbridge.loan.dto.ScheduleResponse;
import java.util.List;
import java.util.UUID;

public interface LoanService {

  LoanResponse create(UUID borrowerUserId, CreateLoanRequest request);

  LoanResponse submit(UUID borrowerUserId, UUID loanId);

  LoanResponse cancel(UUID borrowerUserId, UUID loanId);

  LoanResponse getOwn(UUID borrowerUserId, UUID loanId);

  List<LoanResponse> listOwn(UUID borrowerUserId);

  ScheduleResponse getSchedule(UUID borrowerUserId, UUID loanId);

  AgreementResponse generateAgreement(UUID borrowerUserId, UUID loanId);
}
