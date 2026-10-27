package com.lendbridge.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Raw inbound webhook record, signature-verified or not, kept for audit/replay-debugging —
 * never the source of truth for whether a payment succeeded (that's {@code PaymentIntent}/
 * {@code Payout}'s own status), just a trace of what actually arrived. */
@Entity
@Table(name = "webhook_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookLog {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private ProviderName provider;

  @Column(name = "provider_event_id", nullable = false, unique = true)
  private String providerEventId;

  @Column(name = "raw_body", nullable = false)
  private String rawBody;

  @Column(name = "headers_json")
  private String headersJson;

  @Column(name = "signature_valid", nullable = false)
  private boolean signatureValid;

  @Column(name = "processed", nullable = false)
  private boolean processed;

  @Column(name = "payment_intent_id")
  private UUID paymentIntentId;

  @Column(name = "payout_id")
  private UUID payoutId;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;
}
