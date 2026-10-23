package com.lendbridge.authentication.mapper;

import com.lendbridge.authentication.dto.UserResponse;
import com.lendbridge.authentication.model.User;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

  @Mapping(target = "roles", ignore = true)
  UserResponse toResponse(User user);

  default UserResponse toResponse(User user, List<String> roles) {
    UserResponse response = toResponse(user);
    response.setRoles(roles);
    return response;
  }
}
