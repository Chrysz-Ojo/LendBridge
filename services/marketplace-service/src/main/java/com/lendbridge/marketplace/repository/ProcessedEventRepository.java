package com.lendbridge.marketplace.repository;

import com.lendbridge.marketplace.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {}
