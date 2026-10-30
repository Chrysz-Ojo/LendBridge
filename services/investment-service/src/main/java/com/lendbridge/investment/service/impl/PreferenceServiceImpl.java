package com.lendbridge.investment.service.impl;

import com.lendbridge.investment.dto.PreferenceRequest;
import com.lendbridge.investment.dto.PreferenceResponse;
import com.lendbridge.investment.model.PreferenceSet;
import com.lendbridge.investment.repository.PreferenceSetRepository;
import com.lendbridge.investment.service.PreferenceService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreferenceServiceImpl implements PreferenceService {

  private final PreferenceSetRepository preferenceSetRepository;

  @Override
  @Transactional
  public PreferenceResponse create(UUID lenderUserId, PreferenceRequest request) {
    Instant now = Instant.now();
    PreferenceSet preferenceSet =
        PreferenceSet.builder()
            .id(UuidV7.generate())
            .lenderUserId(lenderUserId)
            .amountPerLoanKobo(request.getAmountPerLoanKobo())
            .totalBudgetKobo(request.getTotalBudgetKobo())
            .minRateBps(request.getMinRateBps())
            .minTenorMonths(request.getMinTenorMonths())
            .maxTenorMonths(request.getMaxTenorMonths())
            .createdAt(now)
            .updatedAt(now)
            .build();
    preferenceSetRepository.save(preferenceSet);
    return toResponse(preferenceSet);
  }

  @Override
  @Transactional
  public PreferenceResponse update(UUID lenderUserId, UUID id, PreferenceRequest request) {
    PreferenceSet preferenceSet = findOwned(lenderUserId, id);
    preferenceSet.setAmountPerLoanKobo(request.getAmountPerLoanKobo());
    preferenceSet.setTotalBudgetKobo(request.getTotalBudgetKobo());
    preferenceSet.setMinRateBps(request.getMinRateBps());
    preferenceSet.setMinTenorMonths(request.getMinTenorMonths());
    preferenceSet.setMaxTenorMonths(request.getMaxTenorMonths());
    preferenceSet.setUpdatedAt(Instant.now());
    preferenceSetRepository.save(preferenceSet);
    return toResponse(preferenceSet);
  }

  @Override
  @Transactional
  public void delete(UUID lenderUserId, UUID id) {
    preferenceSetRepository.delete(findOwned(lenderUserId, id));
  }

  @Override
  @Transactional(readOnly = true)
  public List<PreferenceResponse> listMine(UUID lenderUserId) {
    return preferenceSetRepository.findAllByLenderUserIdOrderByCreatedAtDesc(lenderUserId).stream()
        .map(this::toResponse)
        .toList();
  }

  private PreferenceSet findOwned(UUID lenderUserId, UUID id) {
    PreferenceSet preferenceSet =
        preferenceSetRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("PREFERENCE_SET_NOT_FOUND", "Preference set not found"));
    if (!preferenceSet.getLenderUserId().equals(lenderUserId)) {
      throw new NotFoundException("PREFERENCE_SET_NOT_FOUND", "Preference set not found");
    }
    return preferenceSet;
  }

  private PreferenceResponse toResponse(PreferenceSet preferenceSet) {
    return PreferenceResponse.builder()
        .id(preferenceSet.getId())
        .amountPerLoanKobo(preferenceSet.getAmountPerLoanKobo())
        .totalBudgetKobo(preferenceSet.getTotalBudgetKobo())
        .minRateBps(preferenceSet.getMinRateBps())
        .minTenorMonths(preferenceSet.getMinTenorMonths())
        .maxTenorMonths(preferenceSet.getMaxTenorMonths())
        .createdAt(preferenceSet.getCreatedAt())
        .updatedAt(preferenceSet.getUpdatedAt())
        .build();
  }
}
