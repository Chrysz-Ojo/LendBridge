package com.lendbridge.notification.repository;

import com.lendbridge.notification.model.DeliveryAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {}
