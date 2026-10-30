package com.lendbridge.investment.service;

import com.lendbridge.investment.dto.CommitmentRequest;
import com.lendbridge.investment.dto.CommitmentResponse;
import java.util.List;
import java.util.UUID;

public interface CommitmentService {

  CommitmentResponse create(UUID lenderUserId, UUID loanId, CommitmentRequest request, String idempotencyKey);

  CommitmentResponse cancel(UUID lenderUserId, UUID commitmentId);

  List<CommitmentResponse> listMine(UUID lenderUserId);
}
