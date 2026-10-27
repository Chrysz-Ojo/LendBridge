package com.lendbridge.payment.authentication;

import com.lendbridge.starter.exception.DependencyUnavailableException;
import com.lendbridge.starter.exception.UnauthorizedException;
import feign.FeignException;
import org.springframework.stereotype.Service;

/**
 * Verifies a withdrawal's 2FA step-up code. This is a security control, not a staleness-tolerant
 * read like wallet-service's {@code LedgerGateway} balance cache — on any failure to actually
 * reach authentication-service, this fails closed ({@link DependencyUnavailableException}),
 * never defaults to "assume verified." A genuinely wrong/expired code (401 from
 * authentication-service) is distinguished and re-surfaced as the same rejection, not conflated
 * with "the service is down."
 */
@Service
public class AuthGateway {

  private final AuthClient authClient;

  public AuthGateway(AuthClient authClient) {
    this.authClient = authClient;
  }

  public void verifyStepUp(String code) {
    try {
      authClient.stepUpVerify(new TotpStepUpCommand(code));
    } catch (FeignException.Unauthorized e) {
      throw new UnauthorizedException("TOTP_CODE_INVALID", "Invalid or expired code");
    } catch (Exception e) {
      throw new DependencyUnavailableException(
          "AUTH_SERVICE_UNAVAILABLE", "Unable to verify the step-up code: " + e.getMessage());
    }
  }
}
