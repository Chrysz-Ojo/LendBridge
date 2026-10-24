package com.lendbridge.document.repository;

import com.lendbridge.document.model.Document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

  Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

  List<Document> findAllByOwnerId(UUID ownerId);
}
