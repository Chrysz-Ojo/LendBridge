package com.lendbridge.adminbff.notification;

import com.lendbridge.adminbff.config.FeignAuthForwardingConfig;
import com.lendbridge.adminbff.dto.ChannelPolicyRequest;
import com.lendbridge.adminbff.dto.ChannelPolicyResponse;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "notification-service",
    url = "${NOTIFICATION_SERVICE_URI:http://localhost:8097}",
    configuration = FeignAuthForwardingConfig.class)
public interface ChannelPolicyClient {

  @GetMapping("/api/v1/notifications/admin/channel-policies")
  ApiResponse<List<ChannelPolicyResponse>> listAll();

  @PutMapping("/api/v1/notifications/admin/channel-policies/{eventType}")
  ApiResponse<ChannelPolicyResponse> upsert(
      @PathVariable("eventType") String eventType, @RequestBody ChannelPolicyRequest request);
}
