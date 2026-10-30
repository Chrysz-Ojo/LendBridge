package com.lendbridge.investment.service;

import com.lendbridge.investment.dto.PreferenceRequest;
import com.lendbridge.investment.dto.PreferenceResponse;
import java.util.List;
import java.util.UUID;

public interface PreferenceService {

  PreferenceResponse create(UUID lenderUserId, PreferenceRequest request);

  PreferenceResponse update(UUID lenderUserId, UUID id, PreferenceRequest request);

  void delete(UUID lenderUserId, UUID id);

  List<PreferenceResponse> listMine(UUID lenderUserId);
}
