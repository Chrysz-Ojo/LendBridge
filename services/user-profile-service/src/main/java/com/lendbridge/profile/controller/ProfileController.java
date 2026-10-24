package com.lendbridge.profile.controller;

import com.lendbridge.profile.dto.AddRoleRequest;
import com.lendbridge.profile.dto.ProfileResponse;
import com.lendbridge.profile.dto.RoleListResponse;
import com.lendbridge.profile.dto.UpdateProfileRequest;
import com.lendbridge.profile.security.CurrentUser;
import com.lendbridge.profile.service.ProfileService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

  private final ProfileService profileService;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(Authentication authentication) {
    ProfileResponse profile = profileService.getProfile(CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(profile));
  }

  @PatchMapping("/me")
  public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
      Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
    ProfileResponse profile =
        profileService.updateProfile(CurrentUser.idFrom(authentication), request);
    return ResponseEntity.ok(ApiResponse.success(profile));
  }

  @PostMapping("/me/roles")
  public ResponseEntity<ApiResponse<ProfileResponse>> addRole(
      Authentication authentication, @Valid @RequestBody AddRoleRequest request) {
    ProfileResponse profile = profileService.addRole(CurrentUser.idFrom(authentication), request);
    return ResponseEntity.ok(ApiResponse.success(profile));
  }

  @GetMapping("/me/roles")
  public ResponseEntity<ApiResponse<RoleListResponse>> getRoles(Authentication authentication) {
    ProfileResponse profile = profileService.getProfile(CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(new RoleListResponse(profile.getRoles())));
  }
}
