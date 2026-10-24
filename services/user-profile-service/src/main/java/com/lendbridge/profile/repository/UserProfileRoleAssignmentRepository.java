package com.lendbridge.profile.repository;

import com.lendbridge.profile.model.ProfileRole;
import com.lendbridge.profile.model.UserProfileRoleAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRoleAssignmentRepository
    extends JpaRepository<UserProfileRoleAssignment, UUID> {

  List<UserProfileRoleAssignment> findByUserId(UUID userId);

  Optional<UserProfileRoleAssignment> findByUserIdAndRole(UUID userId, ProfileRole role);
}
