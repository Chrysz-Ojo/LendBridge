package com.lendbridge.notification.repository;

import com.lendbridge.notification.model.Contact;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, UUID> {}
