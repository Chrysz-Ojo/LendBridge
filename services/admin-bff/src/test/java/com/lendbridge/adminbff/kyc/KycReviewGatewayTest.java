package com.lendbridge.adminbff.kyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lendbridge.adminbff.dto.KycReviewQueueItemResponse;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Representative gateway unit test — mocks the Feign client interface directly, same shape as
 * every other integration's gateway, which is why only this one (plus
 * {@code RoleAssignmentGatewayTest}) gets its own test rather than all 11 individually. */
class KycReviewGatewayTest {

  @Test
  void getQueue_unwrapsApiResponseData() {
    KycReviewClient client = mock(KycReviewClient.class);
    KycReviewQueueItemResponse item = KycReviewQueueItemResponse.builder().id(UUID.randomUUID()).status("PENDING").build();
    when(client.getQueue()).thenReturn(ApiResponse.success(List.of(item)));

    KycReviewGateway gateway = new KycReviewGateway(client);
    List<KycReviewQueueItemResponse> result = gateway.getQueue();

    assertThat(result).containsExactly(item);
  }
}
