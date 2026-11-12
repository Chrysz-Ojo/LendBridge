package com.lendbridge.support.repository;

import com.lendbridge.support.model.Faq;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, UUID> {

  /** Search filtering (case-insensitive question/answer match) is done in {@code FaqServiceImpl}
   * over this result — the expected FAQ set is small, so a derived query combining AND/OR across
   * two fields was judged not worth the awkward method-name syntax it would require. */
  List<Faq> findByPublishedTrueOrderByCreatedAtAsc();
}
