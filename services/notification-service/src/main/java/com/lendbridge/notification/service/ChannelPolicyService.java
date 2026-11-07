package com.lendbridge.notification.service;

import com.lendbridge.notification.dto.ChannelPolicyRequest;
import com.lendbridge.notification.dto.ChannelPolicyResponse;
import com.lendbridge.notification.model.NotificationType;
import java.util.List;

public interface ChannelPolicyService {
  List<ChannelPolicyResponse> listAll();

  ChannelPolicyResponse upsert(NotificationType eventType, ChannelPolicyRequest request);
}
