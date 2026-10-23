package com.lendbridge.authentication.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
  private String message;

  public static MessageResponse of(String message) {
    return new MessageResponse(message);
  }
}
