package com.lendbridge.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadUrlResponse {
  private String url;
  private long expiresAtEpochMillis;
}
