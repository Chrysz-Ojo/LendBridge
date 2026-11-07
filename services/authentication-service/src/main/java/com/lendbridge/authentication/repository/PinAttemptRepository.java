package com.lendbridge.authentication.repository;

import com.lendbridge.authentication.model.PinAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PinAttemptRepository extends JpaRepository<PinAttempt, UUID> {}
