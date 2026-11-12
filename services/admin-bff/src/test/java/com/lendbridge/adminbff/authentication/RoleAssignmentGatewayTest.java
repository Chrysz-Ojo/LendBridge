package com.lendbridge.adminbff.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lendbridge.adminbff.dto.Role;
import com.lendbridge.adminbff.dto.RoleAssignmentRequest;
import com.lendbridge.adminbff.dto.RoleListResponse;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleAssignmentGatewayTest {

  @Test
  void assignRole_unwrapsApiResponseData() {
    RoleAssignmentClient client = mock(RoleAssignmentClient.class);
    UUID targetUserId = UUID.randomUUID();
    RoleAssignmentRequest request = new RoleAssignmentRequest(Role.SUPER_ADMIN);
    RoleListResponse expected = new RoleListResponse(List.of(Role.SUPER_ADMIN));
    when(client.assignRole(targetUserId, request)).thenReturn(ApiResponse.success(expected));

    RoleAssignmentGateway gateway = new RoleAssignmentGateway(client);
    RoleListResponse result = gateway.assignRole(targetUserId, request);

    assertThat(result.getRoles()).containsExactly(Role.SUPER_ADMIN);
  }
}
