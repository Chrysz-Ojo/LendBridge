package com.lendbridge.notification.provider;

public interface SmsSender {
  void send(String phone, String message);
}
