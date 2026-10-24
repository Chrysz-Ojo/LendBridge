package com.lendbridge.profile.mapper;

import com.lendbridge.profile.dto.ProfileResponse;
import com.lendbridge.profile.model.ProfileRole;
import com.lendbridge.profile.model.UserProfile;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProfileMapper {

  @Mapping(target = "roles", ignore = true)
  ProfileResponse toResponse(UserProfile profile);

  default ProfileResponse toResponse(UserProfile profile, List<ProfileRole> roles) {
    ProfileResponse response = toResponse(profile);
    response.setRoles(roles);
    return response;
  }
}
