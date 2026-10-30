package com.lendbridge.investment.repository;

import com.lendbridge.investment.model.PreferenceSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferenceSetRepository extends JpaRepository<PreferenceSet, UUID> {

  List<PreferenceSet> findAllByLenderUserIdOrderByCreatedAtDesc(UUID lenderUserId);
}
