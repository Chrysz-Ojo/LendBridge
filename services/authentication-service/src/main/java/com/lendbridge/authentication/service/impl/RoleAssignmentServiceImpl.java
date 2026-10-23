package com.lendbridge.authentication.service.impl;

import com.lendbridge.authentication.service.RoleAssignmentService;

import com.lendbridge.authentication.dto.RoleAssignmentRequest;
import com.lendbridge.authentication.model.Role;
import com.lendbridge.authentication.model.UserRoleAssignment;
import com.lendbridge.authentication.repository.UserRoleAssignmentRepository;
import com.lendbridge.authentication.crypto.UuidV7;
import com.lendbridge.authentication.exception.NotFoundException;
import com.lendbridge.authentication.repository.UserJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleAssignmentServiceImpl implements RoleAssignmentService {

  private final UserRoleAssignmentRepository repository;
  private final UserJpaRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public List<Role> rolesOf(UUID userId) {
    return repository.findByUserId(userId).stream().map(UserRoleAssignment::getRole).toList();
  }

  @Override
  @Transactional
  public void assign(UUID targetUserId, RoleAssignmentRequest request, UUID grantedBy) {
    if (!userRepository.existsById(targetUserId)) {
      throw new NotFoundException("USER_NOT_FOUND", "User not found");
    }
    Role role = request.getRole();
    if (repository.existsByUserIdAndRole(targetUserId, role)) {
      return;
    }
    repository.save(
        UserRoleAssignment.builder()
            .id(UuidV7.generate())
            .userId(targetUserId)
            .role(role)
            .grantedBy(grantedBy)
            .createdAt(Instant.now())
            .build());
  }
}
