package com.lendbridge.authentication.security;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Supplies the RSA keypair this service signs its own JWTs with. If {@code
 * AUTH_JWT_PRIVATE_KEY_PEM} / {@code AUTH_JWT_PUBLIC_KEY_PEM} are configured, those PEM-encoded
 * keys are used; otherwise an ephemeral in-memory keypair is generated at startup. A "prod"
 * profile without configured keys fails fast rather than silently issuing tokens nobody else can
 * validate across restarts.
 */
@Component
@Getter
public class JwtKeyProvider {

  private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

  private final RSAPrivateKey privateKey;
  private final RSAPublicKey publicKey;
  private final String keyId;

  public JwtKeyProvider(
      @Value("${auth.jwt.private-key-pem:}") String privateKeyPem,
      @Value("${auth.jwt.public-key-pem:}") String publicKeyPem,
      Environment environment) {
    if (!privateKeyPem.isBlank() && !publicKeyPem.isBlank()) {
      this.privateKey = parsePrivateKey(privateKeyPem);
      this.publicKey = parsePublicKey(publicKeyPem);
      this.keyId = "configured-key";
    } else {
      if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
        throw new IllegalStateException(
            "AUTH_JWT_PRIVATE_KEY_PEM/AUTH_JWT_PUBLIC_KEY_PEM must be set under the prod profile");
      }
      log.warn(
          "No AUTH_JWT_PRIVATE_KEY_PEM configured; generating an ephemeral in-memory RSA keypair "
              + "for this process only. Do not use this outside local development and tests.");
      KeyPair generated = generateKeyPair();
      this.privateKey = (RSAPrivateKey) generated.getPrivate();
      this.publicKey = (RSAPublicKey) generated.getPublic();
      this.keyId = "ephemeral-" + UUID.randomUUID();
    }
  }

  private static KeyPair generateKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA key generation algorithm unavailable", e);
    }
  }

  private static RSAPrivateKey parsePrivateKey(String pem) {
    try {
      String cleaned = stripPem(pem);
      byte[] decoded = Base64.getMimeDecoder().decode(cleaned);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("Invalid AUTH_JWT_PRIVATE_KEY_PEM", e);
    }
  }

  private static RSAPublicKey parsePublicKey(String pem) {
    try {
      String cleaned = stripPem(pem);
      byte[] decoded = Base64.getMimeDecoder().decode(cleaned);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("Invalid AUTH_JWT_PUBLIC_KEY_PEM", e);
    }
  }

  private static String stripPem(String pem) {
    return pem.replaceAll("-----BEGIN (.*)-----", "")
        .replaceAll("-----END (.*)-----", "")
        .replaceAll("\\s", "");
  }
}
