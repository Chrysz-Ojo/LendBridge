package com.lendbridge.authentication.service;

import com.lendbridge.authentication.dto.RoleAssignmentRequest;
import com.lendbridge.authentication.model.Role;
import java.util.List;
import java.util.UUID;

public interface RoleAssignmentService {

  List<Role> rolesOf(UUID userId);

  void assign(UUID targetUserId, RoleAssignmentRequest request, UUID grantedBy);
}
