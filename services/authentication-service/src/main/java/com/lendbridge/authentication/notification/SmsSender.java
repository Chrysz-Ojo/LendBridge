package com.lendbridge.authentication.notification;

/** Port for sending SMS. A real provider adapter (Termii/Africa's Talking/Twilio) can implement
 * this later behind the same interface, per CLAUDE.md's external-provider rule. */
public interface SmsSender {

  void send(String toPhone, String message);
}
