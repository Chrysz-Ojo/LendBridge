package com.lendbridge.authentication.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * RFC 6238 Appendix B published test vectors for the SHA-1 mode, seed "12345678901234567890",
 * 8-digit codes, 30-second step, T0=0.
 */
class TotpAlgorithmTest {

  private static final byte[] SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

  @ParameterizedTest
  @CsvSource({
    "59, 94287082",
    "1111111109, 07081804",
    "1111111111, 14050471",
    "1234567890, 89005924",
    "2000000000, 69279037",
    "20000000000, 65353130"
  })
  void matchesRfc6238PublishedVectors(long epochSeconds, String expectedCode) {
    assertThat(TotpAlgorithm.generateAtTime(SEED, epochSeconds, 30, 8)).isEqualTo(expectedCode);
  }
}
