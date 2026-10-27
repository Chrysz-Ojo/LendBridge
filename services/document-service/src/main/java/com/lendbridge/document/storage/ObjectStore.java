package com.lendbridge.document.storage;

import java.net.URL;
import java.time.Duration;

/** Port for storing document bytes and generating time-limited download links. */
public interface ObjectStore {

  void put(String key, byte[] content, String contentType);

  URL presignedGetUrl(String key, Duration expiry);
}
