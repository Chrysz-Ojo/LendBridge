package com.lendbridge.authentication.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JSON shape stored in the outbox row; mirrors the fields of the user_registered.avsc schema. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredPayload {
  private String userId;
  private String email;
  private boolean emailVerified;
  private boolean phoneVerified;
  private long registeredAtEpochMillis;
}
