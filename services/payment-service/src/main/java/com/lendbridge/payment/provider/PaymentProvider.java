package com.lendbridge.payment.provider;

import com.lendbridge.payment.model.ProviderName;
import java.util.Map;

/**
 * Port behind which every PSP integration sits, per CLAUDE.md's external-provider rule — a fake
 * implementation is what every test and CI run exercises; the real adapters
 * ({@code PaystackPaymentProvider}, {@code FlutterwavePaymentProvider},
 * {@code MonnifyPaymentProvider}) are production code for whoever has real sandbox credentials,
 * never called by anything automated in this repo.
 *
 * <p>Webhook signature verification is deliberately a per-adapter concern, not something a
 * controller implements generically — Paystack (HMAC-SHA512 of the raw body), Flutterwave (a
 * static shared-secret string compare, not HMAC — no real body-tamper protection), and Monnify
 * (HMAC-SHA512, possibly absent in sandbox) all differ genuinely, confirmed via direct review of
 * each provider's current docs.
 */
public interface PaymentProvider {

  ProviderName name();

  ProviderChargeResult initiateCharge(ChargeRequest request);

  ProviderChargeResult verifyCharge(String providerReference);

  ProviderVirtualAccountResult provisionVirtualAccount(VirtualAccountRequest request);

  ProviderPayoutResult initiatePayout(PayoutRequest request);

  boolean verifySignature(byte[] rawBody, Map<String, String> headers);

  ProviderWebhookEvent parseWebhookEvent(byte[] rawBody, Map<String, String> headers);
}
