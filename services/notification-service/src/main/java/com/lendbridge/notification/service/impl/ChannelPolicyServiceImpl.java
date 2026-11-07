package com.lendbridge.notification.service.impl;

import com.lendbridge.notification.dto.ChannelPolicyRequest;
import com.lendbridge.notification.dto.ChannelPolicyResponse;
import com.lendbridge.notification.model.ChannelPolicy;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.repository.ChannelPolicyRepository;
import com.lendbridge.notification.service.ChannelPolicyService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelPolicyServiceImpl implements ChannelPolicyService {

  private final ChannelPolicyRepository repository;

  @Override
  @Transactional(readOnly = true)
  public List<ChannelPolicyResponse> listAll() {
    return repository.findAll().stream().map(this::toResponse).toList();
  }

  @Override
  @Transactional
  public ChannelPolicyResponse upsert(NotificationType eventType, ChannelPolicyRequest request) {
    ChannelPolicy policy = repository.findById(eventType).orElseGet(() -> ChannelPolicy.defaults(eventType));
    policy.setEmailEnabled(request.getEmailEnabled());
    policy.setSmsEnabled(request.getSmsEnabled());
    policy.setInAppEnabled(request.getInAppEnabled());
    policy.setUpdatedAt(Instant.now());
    repository.save(policy);
    return toResponse(policy);
  }

  private ChannelPolicyResponse toResponse(ChannelPolicy policy) {
    return ChannelPolicyResponse.builder()
        .eventType(policy.getEventType().name())
        .emailEnabled(policy.isEmailEnabled())
        .smsEnabled(policy.isSmsEnabled())
        .inAppEnabled(policy.isInAppEnabled())
        .updatedAt(policy.getUpdatedAt())
        .build();
  }
}
