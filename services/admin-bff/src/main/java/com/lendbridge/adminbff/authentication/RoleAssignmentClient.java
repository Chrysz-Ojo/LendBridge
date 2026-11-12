package com.lendbridge.adminbff.authentication;

import com.lendbridge.adminbff.config.FeignAuthForwardingConfig;
import com.lendbridge.adminbff.dto.RoleAssignmentRequest;
import com.lendbridge.adminbff.dto.RoleListResponse;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "authentication-service",
    url = "${AUTH_SERVICE_URI:http://localhost:8081}",
    configuration = FeignAuthForwardingConfig.class)
public interface RoleAssignmentClient {

  @GetMapping("/api/v1/auth/admin/users/{id}/roles")
  ApiResponse<RoleListResponse> listRoles(@PathVariable("id") UUID userId);

  @PostMapping("/api/v1/auth/admin/users/{id}/roles")
  ApiResponse<RoleListResponse> assignRole(
      @PathVariable("id") UUID userId, @RequestBody RoleAssignmentRequest request);
}
