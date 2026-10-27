package com.lendbridge.payment.repository;

import com.lendbridge.payment.model.PaymentIntent;
import com.lendbridge.payment.model.ProviderName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {

  Optional<PaymentIntent> findByIdempotencyKey(String idempotencyKey);

  Optional<PaymentIntent> findByProviderAndProviderReference(
      ProviderName provider, String providerReference);
}
