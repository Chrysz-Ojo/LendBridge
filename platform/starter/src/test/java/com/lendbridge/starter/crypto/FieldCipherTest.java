package com.lendbridge.starter.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class FieldCipherTest {

  private final FieldCipher cipher = new FieldCipher(randomBase64Key());

  @Test
  void roundTripsPlaintext() {
    String plaintext = "+2348012345678";
    String ciphertext = cipher.encrypt(plaintext);
    assertThat(ciphertext).isNotEqualTo(plaintext);
    assertThat(cipher.decrypt(ciphertext)).isEqualTo(plaintext);
  }

  @Test
  void sameInputProducesDifferentCiphertextEachTime() {
    String plaintext = "+2348012345678";
    assertThat(cipher.encrypt(plaintext)).isNotEqualTo(cipher.encrypt(plaintext));
  }

  @Test
  void tamperingWithCiphertextFailsDecryption() {
    String ciphertext = cipher.encrypt("+2348012345678");
    byte[] raw = Base64.getDecoder().decode(ciphertext);
    raw[raw.length - 1] ^= 0x01;
    String tampered = Base64.getEncoder().encodeToString(raw);

    assertThatThrownBy(() -> cipher.decrypt(tampered))
        .isInstanceOf(FieldCipher.FieldDecryptionException.class);
  }

  private static String randomBase64Key() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }
}
