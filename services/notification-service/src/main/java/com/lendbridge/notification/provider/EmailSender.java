package com.lendbridge.notification.provider;

public interface EmailSender {
  void send(String to, String subject, String body);
}
