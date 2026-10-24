package com.lendbridge.profile.service.impl;

import com.lendbridge.profile.dto.AddRoleRequest;
import com.lendbridge.profile.dto.ProfileResponse;
import com.lendbridge.profile.dto.UpdateProfileRequest;
import com.lendbridge.profile.mapper.ProfileMapper;
import com.lendbridge.profile.model.ProfileRole;
import com.lendbridge.profile.model.UserProfile;
import com.lendbridge.profile.model.UserProfileRoleAssignment;
import com.lendbridge.profile.outbox.KafkaTopics;
import com.lendbridge.profile.outbox.OutboxEventWriter;
import com.lendbridge.profile.outbox.ProfileUpdatedPayload;
import com.lendbridge.profile.repository.UserProfileRepository;
import com.lendbridge.profile.repository.UserProfileRoleAssignmentRepository;
import com.lendbridge.profile.service.ProfileService;
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
public class ProfileServiceImpl implements ProfileService {

  private final UserProfileRepository profileRepository;
  private final UserProfileRoleAssignmentRepository roleRepository;
  private final OutboxEventWriter outboxEventWriter;
  private final ProfileMapper profileMapper;

  @Override
  @Transactional(readOnly = true)
  public ProfileResponse getProfile(UUID userId) {
    UserProfile profile = findProfileOrThrow(userId);
    return profileMapper.toResponse(profile, rolesOf(userId));
  }

  @Override
  @Transactional
  public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
    UserProfile profile = findProfileOrThrow(userId);
    applyUpdates(profile, request);
    profile.setUpdatedAt(Instant.now());
    profileRepository.save(profile);
    publishProfileUpdated(userId);
    return profileMapper.toResponse(profile, rolesOf(userId));
  }

  @Override
  @Transactional
  public ProfileResponse addRole(UUID userId, AddRoleRequest request) {
    UserProfile profile = findProfileOrThrow(userId);
    boolean alreadyHeld = roleRepository.findByUserIdAndRole(userId, request.getRole()).isPresent();
    if (!alreadyHeld) {
      Instant now = Instant.now();
      roleRepository.save(
          UserProfileRoleAssignment.builder()
              .id(UuidV7.generate())
              .userId(userId)
              .role(request.getRole())
              .createdAt(now)
              .build());
      profile.setUpdatedAt(now);
      profileRepository.save(profile);
      publishProfileUpdated(userId);
    }
    return profileMapper.toResponse(profile, rolesOf(userId));
  }

  private void applyUpdates(UserProfile profile, UpdateProfileRequest request) {
    if (request.getFullName() != null) {
      profile.setFullName(request.getFullName());
    }
    if (request.getDateOfBirth() != null) {
      profile.setDateOfBirth(request.getDateOfBirth());
    }
    if (request.getAddressLine() != null) {
      profile.setAddressLine(request.getAddressLine());
    }
    if (request.getCity() != null) {
      profile.setCity(request.getCity());
    }
    if (request.getState() != null) {
      profile.setState(request.getState());
    }
    if (request.getCountry() != null) {
      profile.setCountry(request.getCountry());
    }
    if (request.getPhone() != null) {
      profile.setPhone(request.getPhone());
    }
    if (request.getEmail() != null) {
      profile.setEmail(request.getEmail());
    }
    if (request.getEmploymentStatus() != null) {
      profile.setEmploymentStatus(request.getEmploymentStatus());
    }
    if (request.getEmployerOrBusinessName() != null) {
      profile.setEmployerOrBusinessName(request.getEmployerOrBusinessName());
    }
    if (request.getMonthlyIncomeKobo() != null) {
      profile.setMonthlyIncomeKobo(request.getMonthlyIncomeKobo());
    }
  }

  private void publishProfileUpdated(UUID userId) {
    List<String> roleNames = rolesOf(userId).stream().map(Enum::name).toList();
    outboxEventWriter.write(
        "UserProfile",
        userId.toString(),
        KafkaTopics.PROFILE_UPDATED,
        new ProfileUpdatedPayload(userId.toString(), roleNames, System.currentTimeMillis()));
  }

  private List<ProfileRole> rolesOf(UUID userId) {
    return roleRepository.findByUserId(userId).stream().map(UserProfileRoleAssignment::getRole).toList();
  }

  private UserProfile findProfileOrThrow(UUID userId) {
    return profileRepository
        .findByUserId(userId)
        .orElseThrow(
            () -> new NotFoundException("PROFILE_NOT_FOUND", "No profile found for this user yet"));
  }
}
