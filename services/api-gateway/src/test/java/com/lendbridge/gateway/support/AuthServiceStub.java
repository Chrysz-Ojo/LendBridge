package com.lendbridge.gateway.support;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * A minimal stand-in for authentication-service: serves a real JWKS endpoint backed by an RSA
 * keypair generated per test run, and echoes a canned 200 for any other path, so IT tests can
 * exercise the gateway's actual routing + JWT validation + rate limiting without a live
 * downstream service or a mocking library.
 */
public final class AuthServiceStub {

  private final DisposableServer server;
  private final RSAPrivateKey privateKey;
  private final String keyId = "test-key";

  private AuthServiceStub(DisposableServer server, RSAPrivateKey privateKey) {
    this.server = server;
    this.privateKey = privateKey;
  }

  public static AuthServiceStub start() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair keyPair = generator.generateKeyPair();
      RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
      RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
      String keyId = "test-key";

      RSAKey jwk =
          new RSAKey.Builder(publicKey)
              .keyID(keyId)
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .build();
      String jwksBody = "{\"keys\":[" + jwk.toPublicJWK().toJSONObject() + "]}";

      DisposableServer server =
          HttpServer.create()
              .port(0)
              .route(
                  routes ->
                      routes
                          .get(
                              "/.well-known/jwks.json",
                              (req, res) ->
                                  res.header("Content-Type", "application/json")
                                      .sendString(Mono.just(jwksBody)))
                          .route(
                              req -> true,
                              (req, res) ->
                                  res.header("Content-Type", "application/json")
                                      .sendString(
                                          Mono.just(
                                              "{\"stub\":\"ok\",\"path\":\""
                                                  + req.path()
                                                  + "\"}"))))
              .bindNow();

      return new AuthServiceStub(server, privateKey);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to start AuthServiceStub", e);
    }
  }

  public String baseUrl() {
    return "http://localhost:" + server.port();
  }

  public String jwksUrl() {
    return baseUrl() + "/.well-known/jwks.json";
  }

  public String signToken(String subject, List<String> roles, long ttlSeconds) {
    try {
      Instant now = Instant.now();
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .subject(subject)
              .issuer("lendbridge-authentication-service")
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
              .jwtID(UUID.randomUUID().toString())
              .claim("roles", roles)
              .build();
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).type(JOSEObjectType.JWT).build(),
              claims);
      jwt.sign(new RSASSASigner(privateKey));
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to sign test token", e);
    }
  }

  public void stop() {
    server.disposeNow();
  }
}
