package com.lendbridge.authentication.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.authentication.security.JwtIssuer;
import com.lendbridge.authentication.support.AbstractIntegrationTest;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JwksIT extends AbstractIntegrationTest {

  @Autowired private JwtIssuer jwtIssuer;

  @Test
  void jwksExposesThePublicKeyThatValidatesIssuedTokens() throws Exception {
    String body =
        mockMvc
            .perform(get("/.well-known/jwks.json"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JWKSet jwkSet = JWKSet.parse(body);
    assertThat(jwkSet.getKeys()).isNotEmpty();
    RSAKey rsaKey = (RSAKey) jwkSet.getKeys().get(0);

    String token =
        jwtIssuer.issueAccessToken(UUID.randomUUID(), List.of("SUPPORT"), true, true, false);
    SignedJWT signedJWT = SignedJWT.parse(token);

    assertThat(signedJWT.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))).isTrue();
  }
}
