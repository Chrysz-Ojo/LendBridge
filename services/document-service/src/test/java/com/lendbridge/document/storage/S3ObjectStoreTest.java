package com.lendbridge.document.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ExtendWith(MockitoExtension.class)
class S3ObjectStoreTest {

  private static final String BUCKET = "test-bucket";

  @Mock private S3Client s3Client;
  @Mock private S3Presigner s3Presigner;
  @Captor private ArgumentCaptor<PutObjectRequest> putObjectRequestCaptor;

  private S3ObjectStore store;

  @BeforeEach
  void setUp() {
    store = new S3ObjectStore(s3Client, s3Presigner, BUCKET);
  }

  @Test
  void put_requestsServerSideEncryption() {
    store.put("documents/owner/doc/file.png", new byte[] {1, 2, 3}, "image/png");

    verify(s3Client).putObject(putObjectRequestCaptor.capture(), any(RequestBody.class));
    PutObjectRequest request = putObjectRequestCaptor.getValue();
    assertThat(request.serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
    assertThat(request.bucket()).isEqualTo(BUCKET);
    assertThat(request.key()).isEqualTo("documents/owner/doc/file.png");
    assertThat(request.contentType()).isEqualTo("image/png");
  }
}
