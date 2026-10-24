package com.lendbridge.profile.repository;

import com.lendbridge.profile.model.UserProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

  Optional<UserProfile> findByUserId(UUID userId);

  boolean existsByUserId(UUID userId);
}
