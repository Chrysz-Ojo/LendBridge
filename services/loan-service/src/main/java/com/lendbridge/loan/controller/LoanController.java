package com.lendbridge.loan.controller;

import com.lendbridge.loan.dto.AgreementResponse;
import com.lendbridge.loan.dto.CreateLoanRequest;
import com.lendbridge.loan.dto.LoanResponse;
import com.lendbridge.loan.dto.ScheduleResponse;
import com.lendbridge.loan.security.CurrentUser;
import com.lendbridge.loan.service.LoanService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Borrower-facing: create, submit, cancel, view and get an agreement for one's own loan
 * requests. */
@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

  private final LoanService loanService;

  @PostMapping
  public ResponseEntity<ApiResponse<LoanResponse>> create(
      Authentication authentication, @Valid @RequestBody CreateLoanRequest request) {
    LoanResponse response = loanService.create(CurrentUser.idFrom(authentication), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PostMapping("/{id}/submit")
  public ResponseEntity<ApiResponse<LoanResponse>> submit(Authentication authentication, @PathVariable UUID id) {
    LoanResponse response = loanService.submit(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<ApiResponse<LoanResponse>> cancel(Authentication authentication, @PathVariable UUID id) {
    LoanResponse response = loanService.cancel(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<List<LoanResponse>>> listMine(Authentication authentication) {
    List<LoanResponse> response = loanService.listOwn(CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<LoanResponse>> get(Authentication authentication, @PathVariable UUID id) {
    LoanResponse response = loanService.getOwn(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/{id}/schedule")
  public ResponseEntity<ApiResponse<ScheduleResponse>> schedule(Authentication authentication, @PathVariable UUID id) {
    ScheduleResponse response = loanService.getSchedule(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{id}/agreement")
  public ResponseEntity<ApiResponse<AgreementResponse>> generateAgreement(
      Authentication authentication, @PathVariable UUID id) {
    AgreementResponse response = loanService.generateAgreement(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
