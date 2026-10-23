package com.lendbridge.authentication.security;

import com.lendbridge.authentication.crypto.UuidV7;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues RS256 JWTs signed with this service's own private key. Spring Security's resource-server
 * support only validates tokens, so issuance is done directly with Nimbus.
 */
@Component
public class JwtIssuer {

  public static final long ACCESS_TOKEN_TTL_MINUTES = 15;
  public static final long MFA_CHALLENGE_TTL_MINUTES = 5;
  public static final String CLAIM_ROLES = "roles";
  public static final String CLAIM_EMAIL_VERIFIED = "email_verified";
  public static final String CLAIM_PHONE_VERIFIED = "phone_verified";
  public static final String CLAIM_MFA_ACTIVE = "mfa_active";
  public static final String CLAIM_PURPOSE = "purpose";
  public static final String PURPOSE_MFA_CHALLENGE = "mfa_challenge";

  private final JwtKeyProvider keyProvider;
  private final String issuer;
  private final String audience;

  public JwtIssuer(
      JwtKeyProvider keyProvider,
      @Value("${auth.jwt.issuer}") String issuer,
      @Value("${auth.jwt.audience}") String audience) {
    this.keyProvider = keyProvider;
    this.issuer = issuer;
    this.audience = audience;
  }

  public String issueAccessToken(
      UUID userId,
      List<String> roles,
      boolean emailVerified,
      boolean phoneVerified,
      boolean mfaActive) {
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .issuer(issuer)
            .audience(audience)
            .claim(CLAIM_ROLES, roles)
            .claim(CLAIM_EMAIL_VERIFIED, emailVerified)
            .claim(CLAIM_PHONE_VERIFIED, phoneVerified)
            .claim(CLAIM_MFA_ACTIVE, mfaActive)
            .jwtID(UuidV7.generate().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(ACCESS_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES)))
            .build();
    return sign(claims);
  }

  public String issueMfaChallengeToken(UUID userId) {
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .issuer(issuer)
            .audience(audience)
            .claim(CLAIM_PURPOSE, PURPOSE_MFA_CHALLENGE)
            .jwtID(UuidV7.generate().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(MFA_CHALLENGE_TTL_MINUTES, ChronoUnit.MINUTES)))
            .build();
    return sign(claims);
  }

  private String sign(JWTClaimsSet claims) {
    try {
      JWSHeader header =
          new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyProvider.getKeyId()).build();
      SignedJWT signedJwt = new SignedJWT(header, claims);
      signedJwt.sign(new RSASSASigner(keyProvider.getPrivateKey()));
      return signedJwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Unable to sign JWT", e);
    }
  }
}
