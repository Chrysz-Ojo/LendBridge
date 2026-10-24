package com.lendbridge.profile.service;

import com.lendbridge.profile.dto.AddRoleRequest;
import com.lendbridge.profile.dto.ProfileResponse;
import com.lendbridge.profile.dto.UpdateProfileRequest;
import java.util.UUID;

public interface ProfileService {

  ProfileResponse getProfile(UUID userId);

  ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);

  ProfileResponse addRole(UUID userId, AddRoleRequest request);
}
