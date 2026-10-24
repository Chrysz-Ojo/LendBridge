package com.lendbridge.profile.outbox;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JSON shape stored in the outbox row; mirrors the fields of the profile_updated.avsc schema. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdatedPayload {
  private String userId;
  private List<String> roles;
  private long updatedAtEpochMillis;
}
