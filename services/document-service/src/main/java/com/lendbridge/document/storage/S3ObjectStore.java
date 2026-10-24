package com.lendbridge.document.storage;

import jakarta.annotation.PostConstruct;
import java.net.URL;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class S3ObjectStore implements ObjectStore {

  private static final Logger log = LoggerFactory.getLogger(S3ObjectStore.class);

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final String bucket;

  public S3ObjectStore(
      S3Client s3Client,
      S3Presigner s3Presigner,
      @Value("${documents.storage.bucket}") String bucket) {
    this.s3Client = s3Client;
    this.s3Presigner = s3Presigner;
    this.bucket = bucket;
  }

  @PostConstruct
  void ensureBucketExists() {
    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    } catch (NoSuchBucketException e) {
      try {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
      } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException alreadyExists) {
        log.debug("Bucket {} already exists, created concurrently", bucket);
      }
    }
  }

  @Override
  public void put(String key, byte[] content, String contentType) {
    s3Client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
        RequestBody.fromBytes(content));
  }

  @Override
  public URL presignedGetUrl(String key, Duration expiry) {
    GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();
    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(expiry)
            .getObjectRequest(getObjectRequest)
            .build();
    return s3Presigner.presignGetObject(presignRequest).url();
  }
}
