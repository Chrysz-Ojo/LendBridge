package com.lendbridge.adminbff.supportfaq;

import com.lendbridge.adminbff.config.FeignAuthForwardingConfig;
import com.lendbridge.adminbff.dto.FaqRequest;
import com.lendbridge.adminbff.dto.FaqResponse;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "support-service-faqs",
    url = "${SUPPORT_SERVICE_URI:http://localhost:8100}",
    configuration = FeignAuthForwardingConfig.class)
public interface SupportFaqClient {

  @PostMapping("/api/v1/support/admin/faqs")
  ApiResponse<FaqResponse> create(@RequestBody FaqRequest request);

  @PutMapping("/api/v1/support/admin/faqs/{faqId}")
  ApiResponse<FaqResponse> update(@PathVariable("faqId") UUID faqId, @RequestBody FaqRequest request);

  @DeleteMapping("/api/v1/support/admin/faqs/{faqId}")
  ApiResponse<Void> delete(@PathVariable("faqId") UUID faqId);
}
