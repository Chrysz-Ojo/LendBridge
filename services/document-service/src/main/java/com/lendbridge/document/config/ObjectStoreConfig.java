package com.lendbridge.document.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Both clients point at the same endpoint via explicit override + path-style access, which works
 * equally against MinIO (local/CI) and real AWS S3 (prod) — only the {@code documents.storage.*}
 * property values differ between environments, never the code.
 */
@Configuration
public class ObjectStoreConfig {

  @Bean
  public S3Client s3Client(
      @Value("${documents.storage.endpoint}") String endpoint,
      @Value("${documents.storage.region:us-east-1}") String region,
      @Value("${documents.storage.access-key}") String accessKey,
      @Value("${documents.storage.secret-key}") String secretKey,
      @Value("${documents.storage.path-style-access:true}") boolean pathStyleAccess) {
    return S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build())
        .build();
  }

  @Bean
  public S3Presigner s3Presigner(
      @Value("${documents.storage.endpoint}") String endpoint,
      @Value("${documents.storage.region:us-east-1}") String region,
      @Value("${documents.storage.access-key}") String accessKey,
      @Value("${documents.storage.secret-key}") String secretKey,
      @Value("${documents.storage.path-style-access:true}") boolean pathStyleAccess) {
    return S3Presigner.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build())
        .build();
  }
}
