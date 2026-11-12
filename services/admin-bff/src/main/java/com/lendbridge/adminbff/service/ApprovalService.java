package com.lendbridge.adminbff.service;

import com.lendbridge.adminbff.dto.ApprovalResponse;
import com.lendbridge.adminbff.dto.DecideApprovalRequest;
import com.lendbridge.adminbff.dto.RaiseApprovalRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;

public interface ApprovalService {

  ApprovalResponse raise(RaiseApprovalRequest request, UUID raisedByUserId, Authentication authentication);

  List<ApprovalResponse> findPending();

  ApprovalResponse decide(
      UUID approvalId, DecideApprovalRequest request, UUID decidedByUserId, Authentication authentication);
}
