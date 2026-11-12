package com.lendbridge.support.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.support.dto.FaqRequest;
import com.lendbridge.support.model.Faq;
import com.lendbridge.support.model.TicketCategory;
import com.lendbridge.support.repository.FaqRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FaqServiceImplTest {

  private FaqRepository faqRepository;
  private FaqServiceImpl service;

  @BeforeEach
  void setUp() {
    faqRepository = mock(FaqRepository.class);
    service = new FaqServiceImpl(faqRepository);
  }

  @Test
  void create_withNoPublishedFlag_defaultsToPublished() {
    FaqRequest request = new FaqRequest();
    request.setCategory(TicketCategory.LOAN);
    request.setQuestion("How long does funding take?");
    request.setAnswer("Usually under 72 hours.");

    var response = service.create(request);

    assertThat(response.isPublished()).isTrue();
  }

  @Test
  void create_withPublishedFalse_staysDraft() {
    FaqRequest request = new FaqRequest();
    request.setCategory(TicketCategory.LOAN);
    request.setQuestion("Q");
    request.setAnswer("A");
    request.setPublished(false);

    var response = service.create(request);

    assertThat(response.isPublished()).isFalse();
  }

  @Test
  void findPublished_noQuery_returnsAllPublished() {
    when(faqRepository.findByPublishedTrueOrderByCreatedAtAsc()).thenReturn(List.of(faq("How to repay?", "Visit wallet.")));

    var result = service.findPublished(null);

    assertThat(result).hasSize(1);
  }

  @Test
  void findPublished_withQuery_filtersCaseInsensitivelyOnQuestionOrAnswer() {
    when(faqRepository.findByPublishedTrueOrderByCreatedAtAsc())
        .thenReturn(List.of(faq("How to repay a loan?", "Visit your wallet."), faq("What is KYC?", "Identity verification.")));

    var result = service.findPublished("REPAY");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getQuestion()).contains("repay");
  }

  @Test
  void delete_removesExistingFaq() {
    Faq faq = faq("Q", "A");
    when(faqRepository.findById(faq.getId())).thenReturn(Optional.of(faq));

    service.delete(faq.getId());

    verify(faqRepository).delete(faq);
  }

  private static Faq faq(String question, String answer) {
    Instant now = Instant.now();
    return Faq.builder()
        .id(UUID.randomUUID())
        .category(TicketCategory.OTHER)
        .question(question)
        .answer(answer)
        .published(true)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
