package com.lendbridge.authentication.notification;

/** Port for sending transactional email. A real provider adapter (SES/SendGrid/Postmark) can
 * implement this later behind the same interface, per CLAUDE.md's external-provider rule. */
public interface EmailSender {

  void send(String toEmail, String subject, String body);
}
