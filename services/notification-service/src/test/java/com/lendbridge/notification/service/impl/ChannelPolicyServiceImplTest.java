package com.lendbridge.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.notification.dto.ChannelPolicyRequest;
import com.lendbridge.notification.dto.ChannelPolicyResponse;
import com.lendbridge.notification.model.ChannelPolicy;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.repository.ChannelPolicyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChannelPolicyServiceImplTest {

  @Mock private ChannelPolicyRepository repository;

  private ChannelPolicyServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new ChannelPolicyServiceImpl(repository);
  }

  @Test
  void upsert_noExistingRow_createsFromDefaults() {
    when(repository.findById(NotificationType.REPAYMENT_DUE)).thenReturn(Optional.empty());
    ChannelPolicyRequest request = new ChannelPolicyRequest(true, false, true);

    ChannelPolicyResponse response = service.upsert(NotificationType.REPAYMENT_DUE, request);

    ArgumentCaptor<ChannelPolicy> captor = ArgumentCaptor.forClass(ChannelPolicy.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo(NotificationType.REPAYMENT_DUE);
    assertThat(captor.getValue().isSmsEnabled()).isFalse();
    assertThat(response.getEventType()).isEqualTo("REPAYMENT_DUE");
    assertThat(response.isSmsEnabled()).isFalse();
  }

  @Test
  void upsert_existingRow_updatesInPlace() {
    ChannelPolicy existing =
        ChannelPolicy.builder()
            .eventType(NotificationType.REPAYMENT_DUE)
            .emailEnabled(true)
            .smsEnabled(true)
            .inAppEnabled(true)
            .updatedAt(Instant.now().minusSeconds(3600))
            .build();
    when(repository.findById(NotificationType.REPAYMENT_DUE)).thenReturn(Optional.of(existing));
    ChannelPolicyRequest request = new ChannelPolicyRequest(true, false, true);

    service.upsert(NotificationType.REPAYMENT_DUE, request);

    assertThat(existing.isSmsEnabled()).isFalse();
    verify(repository).save(existing);
  }

  @Test
  void listAll_returnsMappedPolicies() {
    ChannelPolicy policy =
        ChannelPolicy.builder().eventType(NotificationType.MARKETING).emailEnabled(true).smsEnabled(false).inAppEnabled(true).build();
    when(repository.findAll()).thenReturn(List.of(policy));

    List<ChannelPolicyResponse> result = service.listAll();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getEventType()).isEqualTo("MARKETING");
    assertThat(result.get(0).isSmsEnabled()).isFalse();
  }
}
