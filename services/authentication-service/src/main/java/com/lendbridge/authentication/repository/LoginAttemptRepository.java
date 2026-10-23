package com.lendbridge.authentication.repository;

import com.lendbridge.authentication.model.LoginAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

  List<LoginAttempt> findByUserIdAndSuccessTrueOrderByCreatedAtDesc(UUID userId);
}
