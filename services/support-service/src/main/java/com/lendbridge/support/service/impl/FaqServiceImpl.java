package com.lendbridge.support.service.impl;

import com.lendbridge.support.dto.FaqRequest;
import com.lendbridge.support.dto.FaqResponse;
import com.lendbridge.support.model.Faq;
import com.lendbridge.support.repository.FaqRepository;
import com.lendbridge.support.service.FaqService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FaqServiceImpl implements FaqService {

  private final FaqRepository faqRepository;

  public FaqServiceImpl(FaqRepository faqRepository) {
    this.faqRepository = faqRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<FaqResponse> findPublished(String query) {
    List<Faq> published = faqRepository.findByPublishedTrueOrderByCreatedAtAsc();
    if (query == null || query.isBlank()) {
      return published.stream().map(FaqServiceImpl::toResponse).toList();
    }
    String needle = query.toLowerCase(Locale.ROOT);
    return published.stream()
        .filter(
            faq ->
                faq.getQuestion().toLowerCase(Locale.ROOT).contains(needle)
                    || faq.getAnswer().toLowerCase(Locale.ROOT).contains(needle))
        .map(FaqServiceImpl::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public FaqResponse create(FaqRequest request) {
    Instant now = Instant.now();
    Faq faq =
        Faq.builder()
            .id(UuidV7.generate())
            .category(request.getCategory())
            .question(request.getQuestion())
            .answer(request.getAnswer())
            .published(request.getPublished() == null || request.getPublished())
            .createdAt(now)
            .updatedAt(now)
            .build();
    faqRepository.save(faq);
    return toResponse(faq);
  }

  @Override
  @Transactional
  public FaqResponse update(UUID faqId, FaqRequest request) {
    Faq faq = findOrThrow(faqId);
    faq.setCategory(request.getCategory());
    faq.setQuestion(request.getQuestion());
    faq.setAnswer(request.getAnswer());
    if (request.getPublished() != null) {
      faq.setPublished(request.getPublished());
    }
    faq.setUpdatedAt(Instant.now());
    faqRepository.save(faq);
    return toResponse(faq);
  }

  @Override
  @Transactional
  public void delete(UUID faqId) {
    Faq faq = findOrThrow(faqId);
    faqRepository.delete(faq);
  }

  private Faq findOrThrow(UUID faqId) {
    return faqRepository.findById(faqId).orElseThrow(() -> new NotFoundException("FAQ_NOT_FOUND", "FAQ not found"));
  }

  private static FaqResponse toResponse(Faq faq) {
    return FaqResponse.builder()
        .id(faq.getId())
        .category(faq.getCategory())
        .question(faq.getQuestion())
        .answer(faq.getAnswer())
        .published(faq.isPublished())
        .createdAt(faq.getCreatedAt())
        .updatedAt(faq.getUpdatedAt())
        .build();
  }
}
