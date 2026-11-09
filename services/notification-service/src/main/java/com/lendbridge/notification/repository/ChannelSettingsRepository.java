package com.lendbridge.notification.repository;

import com.lendbridge.notification.model.ChannelSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelSettingsRepository extends JpaRepository<ChannelSettings, UUID> {}
