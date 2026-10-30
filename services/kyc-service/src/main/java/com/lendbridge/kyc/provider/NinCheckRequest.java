package com.lendbridge.kyc.provider;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Identical shape to {@link BvnCheckRequest} for NIN — see that class's javadoc. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NinCheckRequest {
  private String nin;
  private String expectedFullName;
  private LocalDate expectedDateOfBirth;
}
