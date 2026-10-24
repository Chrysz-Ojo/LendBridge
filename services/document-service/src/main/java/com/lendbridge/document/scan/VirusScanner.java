package com.lendbridge.document.scan;

/** Port for scanning uploaded file content. A real ClamAV adapter can implement this later
 * behind the same interface, per CLAUDE.md's external-provider rule. */
public interface VirusScanner {

  ScanResult scan(byte[] content);
}
