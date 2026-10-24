package com.lendbridge.profile.repository;

import com.lendbridge.profile.model.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  @Query(
      value =
          "SELECT * FROM outbox_event WHERE status = 'PENDING' "
              + "ORDER BY created_at ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<OutboxEvent> lockNextBatch(@Param("batchSize") int batchSize);
}
