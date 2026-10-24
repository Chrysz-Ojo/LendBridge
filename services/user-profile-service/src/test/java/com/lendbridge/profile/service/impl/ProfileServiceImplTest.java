package com.lendbridge.profile.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.profile.dto.AddRoleRequest;
import com.lendbridge.profile.dto.UpdateProfileRequest;
import com.lendbridge.profile.mapper.ProfileMapper;
import com.lendbridge.profile.model.ProfileRole;
import com.lendbridge.profile.model.UserProfile;
import com.lendbridge.profile.model.UserProfileRoleAssignment;
import com.lendbridge.profile.outbox.OutboxEventWriter;
import com.lendbridge.profile.repository.UserProfileRepository;
import com.lendbridge.profile.repository.UserProfileRoleAssignmentRepository;
import com.lendbridge.starter.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

  @Mock private UserProfileRepository profileRepository;
  @Mock private UserProfileRoleAssignmentRepository roleRepository;
  @Mock private OutboxEventWriter outboxEventWriter;
  private ProfileMapper profileMapper;

  private ProfileServiceImpl service;
  private UUID userId;
  private UserProfile profile;

  @BeforeEach
  void setUp() {
    profileMapper = org.mapstruct.factory.Mappers.getMapper(ProfileMapper.class);
    service = new ProfileServiceImpl(profileRepository, roleRepository, outboxEventWriter, profileMapper);
    userId = UUID.randomUUID();
    Instant now = Instant.now();
    profile =
        UserProfile.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .email("jane@example.com")
            .createdAt(now)
            .updatedAt(now)
            .build();
  }

  @Test
  void getProfile_throwsNotFound_whenNoProfileExists() {
    when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getProfile(userId)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void getProfile_returnsMappedProfile_withRoles() {
    when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(roleRepository.findByUserId(userId))
        .thenReturn(
            List.of(
                UserProfileRoleAssignment.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .role(ProfileRole.BORROWER)
                    .createdAt(Instant.now())
                    .build()));

    var response = service.getProfile(userId);

    assertThat(response.getUserId()).isEqualTo(userId);
    assertThat(response.getRoles()).containsExactly(ProfileRole.BORROWER);
  }

  @Test
  void updateProfile_onlyOverwritesNonNullFields() {
    profile.setFullName("Old Name");
    profile.setCity("Lagos");
    when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(roleRepository.findByUserId(userId)).thenReturn(List.of());

    UpdateProfileRequest request = new UpdateProfileRequest();
    request.setFullName("New Name");

    var response = service.updateProfile(userId, request);

    assertThat(response.getFullName()).isEqualTo("New Name");
    assertThat(response.getCity()).isEqualTo("Lagos");
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void addRole_isIdempotent_whenRoleAlreadyHeld() {
    when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(roleRepository.findByUserIdAndRole(userId, ProfileRole.LENDER))
        .thenReturn(
            Optional.of(
                UserProfileRoleAssignment.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .role(ProfileRole.LENDER)
                    .createdAt(Instant.now())
                    .build()));
    when(roleRepository.findByUserId(userId))
        .thenReturn(
            List.of(
                UserProfileRoleAssignment.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .role(ProfileRole.LENDER)
                    .createdAt(Instant.now())
                    .build()));

    AddRoleRequest request = new AddRoleRequest();
    request.setRole(ProfileRole.LENDER);

    service.addRole(userId, request);

    verify(roleRepository, never()).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void addRole_addsNewRole_andPublishesEvent() {
    when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(roleRepository.findByUserIdAndRole(userId, ProfileRole.BORROWER)).thenReturn(Optional.empty());
    when(roleRepository.findByUserId(userId))
        .thenReturn(
            List.of(
                UserProfileRoleAssignment.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .role(ProfileRole.BORROWER)
                    .createdAt(Instant.now())
                    .build()));

    AddRoleRequest request = new AddRoleRequest();
    request.setRole(ProfileRole.BORROWER);

    service.addRole(userId, request);

    verify(roleRepository).save(any(UserProfileRoleAssignment.class));
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }
}
