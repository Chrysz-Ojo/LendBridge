package com.lendbridge.authentication.repository;

import com.lendbridge.authentication.model.MfaEnrollment;
import com.lendbridge.authentication.model.MfaMethod;
import com.lendbridge.authentication.model.MfaStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaEnrollmentRepository extends JpaRepository<MfaEnrollment, UUID> {

  Optional<MfaEnrollment> findByUserIdAndMethodAndStatus(
      UUID userId, MfaMethod method, MfaStatus status);

  List<MfaEnrollment> findByUserIdAndStatus(UUID userId, MfaStatus status);
}
