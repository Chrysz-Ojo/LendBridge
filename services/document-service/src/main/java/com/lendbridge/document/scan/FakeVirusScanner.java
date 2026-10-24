package com.lendbridge.document.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Fake scanner for local development and tests; see {@code FakeEmailSender} in
 * authentication-service for the same pattern (CLAUDE.md's external-provider rule). */
@Component
public class FakeVirusScanner implements VirusScanner {

  private static final Logger log = LoggerFactory.getLogger(FakeVirusScanner.class);

  @Override
  public ScanResult scan(byte[] content) {
    log.info("Fake virus scan of {} bytes: CLEAN", content.length);
    return ScanResult.CLEAN;
  }
}
