package com.lendbridge.authentication.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtIssuerTest {

  private final JwtKeyProvider keyProvider = new JwtKeyProvider("", "", new MockEnvironment());
  private final JwtIssuer issuer =
      new JwtIssuer(keyProvider, "lendbridge-authentication-service", "lendbridge-platform");

  @Test
  void accessTokenCarriesExpectedClaims() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = issuer.issueAccessToken(userId, List.of("SUPER_ADMIN"), true, true, false);

    SignedJWT jwt = SignedJWT.parse(token);
    var claims = jwt.getJWTClaimsSet();

    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.getIssuer()).isEqualTo("lendbridge-authentication-service");
    assertThat(claims.getAudience()).containsExactly("lendbridge-platform");
    assertThat(claims.getStringListClaim(JwtIssuer.CLAIM_ROLES)).containsExactly("SUPER_ADMIN");
    assertThat(claims.getBooleanClaim(JwtIssuer.CLAIM_EMAIL_VERIFIED)).isTrue();
    assertThat(claims.getBooleanClaim(JwtIssuer.CLAIM_PHONE_VERIFIED)).isTrue();
    assertThat(claims.getBooleanClaim(JwtIssuer.CLAIM_MFA_ACTIVE)).isFalse();
    assertThat(claims.getJWTID()).isNotBlank();
    assertThat(claims.getExpirationTime()).isAfter(claims.getIssueTime());
  }

  @Test
  void mfaChallengeTokenCarriesPurposeClaim() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = issuer.issueMfaChallengeToken(userId);

    SignedJWT jwt = SignedJWT.parse(token);
    var claims = jwt.getJWTClaimsSet();

    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.getStringClaim(JwtIssuer.CLAIM_PURPOSE))
        .isEqualTo(JwtIssuer.PURPOSE_MFA_CHALLENGE);
  }
}
