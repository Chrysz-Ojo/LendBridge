package com.lendbridge.starter.jpa;

import com.lendbridge.starter.crypto.FieldCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Transparently encrypts/decrypts a String column at the JPA boundary via {@link FieldCipher}, so
 * the entity's in-memory field is always plaintext while the database column holds AES-GCM
 * ciphertext. {@code autoApply = false}: opt in per-field with {@code @Convert(converter =
 * EncryptedStringConverter.class)} rather than silently encrypting every String column. Registered
 * as a Spring bean so {@link FieldCipher} can be constructor-injected; Spring Boot wires
 * Hibernate's bean container automatically for this.
 */
@Component
@Converter(autoApply = false)
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

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
