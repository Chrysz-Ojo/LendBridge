package com.lendbridge.document.scan;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Fake scanner for local development and tests; see {@code FakeEmailSender} in
 * authentication-service for the same pattern (CLAUDE.md's external-provider rule). Flags the
 * standard EICAR antivirus test string as infected, exactly as a real AV engine would, so
 * callers can exercise the rejection path deterministically without mocking. */
@Component
public class FakeVirusScanner implements VirusScanner {

  private static final Logger log = LoggerFactory.getLogger(FakeVirusScanner.class);
  private static final String EICAR_SIGNATURE =
      "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

  @Override
  public ScanResult scan(byte[] content) {
    String text = new String(content, StandardCharsets.ISO_8859_1);
    if (text.contains(EICAR_SIGNATURE)) {
      log.warn("Fake virus scan of {} bytes: INFECTED (EICAR test signature)", content.length);
      return ScanResult.INFECTED;
    }
    log.info("Fake virus scan of {} bytes: CLEAN", content.length);
    return ScanResult.CLEAN;
  }
}
