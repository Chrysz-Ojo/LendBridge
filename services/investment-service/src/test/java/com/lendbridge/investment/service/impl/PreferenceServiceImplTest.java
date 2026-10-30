package com.lendbridge.investment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.dto.PreferenceRequest;
import com.lendbridge.investment.dto.PreferenceResponse;
import com.lendbridge.investment.model.PreferenceSet;
import com.lendbridge.investment.repository.PreferenceSetRepository;
import com.lendbridge.starter.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceImplTest {

  @Mock private PreferenceSetRepository preferenceSetRepository;

  private PreferenceServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new PreferenceServiceImpl(preferenceSetRepository);
  }

  private PreferenceRequest request() {
    return new PreferenceRequest(1_000_000L, 10_000_000L, 1500, 3, 12);
  }

  private PreferenceSet preferenceSet(UUID lenderId) {
    Instant now = Instant.now();
    return PreferenceSet.builder()
        .id(UUID.randomUUID())
        .lenderUserId(lenderId)
        .amountPerLoanKobo(1_000_000L)
        .totalBudgetKobo(10_000_000L)
        .minRateBps(1500)
        .minTenorMonths(3)
        .maxTenorMonths(12)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  @Test
  void create_savesAndReturnsIt() {
    UUID lenderId = UUID.randomUUID();

    PreferenceResponse response = service.create(lenderId, request());

    assertThat(response.getAmountPerLoanKobo()).isEqualTo(1_000_000L);
    verify(preferenceSetRepository).save(any());
  }

  @Test
  void update_ownedPreferenceSet_updatesFields() {
    UUID lenderId = UUID.randomUUID();
    PreferenceSet existing = preferenceSet(lenderId);
    when(preferenceSetRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

    PreferenceResponse response =
        service.update(lenderId, existing.getId(), new PreferenceRequest(2_000_000L, 20_000_000L, 1800, 6, 24));

    assertThat(response.getAmountPerLoanKobo()).isEqualTo(2_000_000L);
    assertThat(response.getMaxTenorMonths()).isEqualTo(24);
  }

  @Test
  void update_notOwnedByCaller_throwsNotFound() {
    UUID ownerId = UUID.randomUUID();
    UUID otherLenderId = UUID.randomUUID();
    PreferenceSet existing = preferenceSet(ownerId);
    when(preferenceSetRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.update(otherLenderId, existing.getId(), request()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void delete_ownedPreferenceSet_deletesIt() {
    UUID lenderId = UUID.randomUUID();
    PreferenceSet existing = preferenceSet(lenderId);
    when(preferenceSetRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

    service.delete(lenderId, existing.getId());

    verify(preferenceSetRepository).delete(existing);
  }

  @Test
  void listMine_returnsTheLendersOwnPreferenceSets() {
    UUID lenderId = UUID.randomUUID();
    when(preferenceSetRepository.findAllByLenderUserIdOrderByCreatedAtDesc(lenderId))
        .thenReturn(List.of(preferenceSet(lenderId)));

    List<PreferenceResponse> responses = service.listMine(lenderId);

    assertThat(responses).hasSize(1);
  }
}
