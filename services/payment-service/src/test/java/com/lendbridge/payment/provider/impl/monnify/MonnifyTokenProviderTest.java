package com.lendbridge.payment.provider.impl.monnify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class MonnifyTokenProviderTest {

  @Test
  void getAccessToken_cachesTheTokenAcrossCalls() throws Exception {
    MonnifyAuthClient authClient = mock(MonnifyAuthClient.class);
    when(authClient.login(anyString())).thenReturn(loginResponse("token-1", 3600));

    MonnifyTokenProvider tokenProvider = new MonnifyTokenProvider(authClient, "api-key", "client-secret");

    String first = tokenProvider.getAccessToken();
    String second = tokenProvider.getAccessToken();

    assertThat(first).isEqualTo("token-1");
    assertThat(second).isEqualTo("token-1");
    verify(authClient, times(1)).login(anyString());
  }

  @Test
  void getAccessToken_refreshesOnceTheCachedTokenExpires() throws Exception {
    MonnifyAuthClient authClient = mock(MonnifyAuthClient.class);
    // Expires in 0 seconds (minus the 30s safety margin), so the very next call must refresh.
    when(authClient.login(anyString())).thenReturn(loginResponse("token-1", 0)).thenReturn(loginResponse("token-2", 3600));

    MonnifyTokenProvider tokenProvider = new MonnifyTokenProvider(authClient, "api-key", "client-secret");

    String first = tokenProvider.getAccessToken();
    String second = tokenProvider.getAccessToken();

    assertThat(first).isEqualTo("token-1");
    assertThat(second).isEqualTo("token-2");
    verify(authClient, times(2)).login(anyString());
  }

  private com.fasterxml.jackson.databind.JsonNode loginResponse(String token, long expiresInSeconds) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode body = mapper.createObjectNode();
    body.put("accessToken", token);
    body.put("expiresIn", expiresInSeconds);
    ObjectNode root = mapper.createObjectNode();
    root.set("responseBody", body);
    return root;
  }
}
