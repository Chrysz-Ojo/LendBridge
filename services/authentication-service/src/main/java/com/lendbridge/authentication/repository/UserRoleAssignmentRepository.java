package com.lendbridge.authentication.repository;

import com.lendbridge.authentication.model.Role;
import com.lendbridge.authentication.model.UserRoleAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UUID> {

  List<UserRoleAssignment> findByUserId(UUID userId);

  boolean existsByUserIdAndRole(UUID userId, Role role);
}
