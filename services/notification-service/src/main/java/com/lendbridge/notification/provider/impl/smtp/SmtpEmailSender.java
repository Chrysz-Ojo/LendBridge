package com.lendbridge.notification.provider.impl.smtp;

import com.lendbridge.notification.provider.EmailSender;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.email.provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {
  private final JavaMailSender mailSender;
  private final String from;
  private final String fromName;

  public SmtpEmailSender(
      JavaMailSender mailSender,
      @Value("${notification.email.from}") String from,
      @Value("${notification.email.from-name}") String fromName) {
    this.mailSender = mailSender;
    this.from = from;
    this.fromName = fromName;
  }

  @Override
  public void send(String to, String subject, String body) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setFrom(new InternetAddress(from, fromName));
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(body);
      mailSender.send(message);
    } catch (Exception e) {
      throw new MailSendException("Failed to send email", e);
    }
  }
}
