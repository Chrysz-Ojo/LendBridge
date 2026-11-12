package com.lendbridge.adminbff.repository;

import com.lendbridge.adminbff.model.ApprovalRequest;
import com.lendbridge.adminbff.model.ApprovalStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

  List<ApprovalRequest> findByStatusOrderByCreatedAtAsc(ApprovalStatus status);
}
