package com.lendbridge.adminbff.supportticket;

import com.lendbridge.adminbff.config.FeignAuthForwardingConfig;
import com.lendbridge.adminbff.dto.AdminTicketResponse;
import com.lendbridge.adminbff.dto.UpdateTicketStatusRequest;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "support-service-tickets",
    url = "${SUPPORT_SERVICE_URI:http://localhost:8100}",
    configuration = FeignAuthForwardingConfig.class)
public interface SupportTicketClient {

  @GetMapping("/api/v1/support/admin/tickets")
  ApiResponse<List<AdminTicketResponse>> findForAdminQueue();

  @PatchMapping("/api/v1/support/admin/tickets/{ticketId}/status")
  ApiResponse<AdminTicketResponse> updateStatus(
      @PathVariable("ticketId") UUID ticketId, @RequestBody UpdateTicketStatusRequest request);
}
