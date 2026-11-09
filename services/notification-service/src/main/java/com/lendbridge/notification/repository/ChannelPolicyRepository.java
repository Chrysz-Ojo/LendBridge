package com.lendbridge.notification.repository;

import com.lendbridge.notification.model.ChannelPolicy;
import com.lendbridge.notification.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelPolicyRepository extends JpaRepository<ChannelPolicy, NotificationType> {}
