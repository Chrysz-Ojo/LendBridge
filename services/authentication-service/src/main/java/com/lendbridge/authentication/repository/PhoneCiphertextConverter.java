package com.lendbridge.authentication.repository;

import com.lendbridge.authentication.crypto.FieldCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Transparently encrypts/decrypts the phone number column at the JPA boundary so the entity's
 * in-memory field is always the plaintext E.164 value while the database column holds AES-GCM
 * ciphertext. Registered as a Spring bean so {@link FieldCipher} can be constructor-injected;
 * Spring Boot wires Hibernate's bean container automatically for this.
 */
@Component
@Converter(autoApply = false)
@RequiredArgsConstructor
public class PhoneCiphertextConverter implements AttributeConverter<String, String> {

  private final FieldCipher fieldCipher;

  @Override
  public String convertToDatabaseColumn(String attribute) {
    return attribute == null ? null : fieldCipher.encrypt(attribute);
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    return dbData == null ? null : fieldCipher.decrypt(dbData);
  }
}
