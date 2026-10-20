package io.roseiam.platformsetting;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class MailDeliveryService {

    private final PlatformSettingService settings;

    public MailDeliveryService(PlatformSettingService settings) {
        this.settings = settings;
    }

    public void sendInvitation(String recipient, String token) {
        Map<String, Object> general = settings.generalForMail();
        String link = general.get("frontendBaseUrl") + "/accept-invitation?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        send(
                recipient,
                "You are invited to RoseIAM",
                "Use this one-time link within 72 hours to activate your account:\n" + link);
    }

    public void sendPasswordReset(String recipient, String token) {
        Map<String, Object> general = settings.generalForMail();
        String link = general.get("frontendBaseUrl") + "/reset-password?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        send(
                recipient,
                "Reset your RoseIAM password",
                "Use this one-time link within 30 minutes to reset your password:\n" + link);
    }

    private void send(String recipient, String subject, String body) {
        Map<String, Object> configuration = settings.mailForDelivery();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(configuration.get("smtpHost").toString());
        sender.setPort(((Number) configuration.get("smtpPort")).intValue());
        boolean auth = Boolean.TRUE.equals(configuration.get("smtpAuthEnabled"));
        if (auth) {
            sender.setUsername(configuration.get("smtpUsername").toString());
            sender.setPassword(settings.decryptPassword(configuration));
        }
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", Boolean.toString(auth));
        properties.put(
                "mail.smtp.starttls.enable",
                Boolean.toString(Boolean.TRUE.equals(configuration.get("smtpStartTlsEnabled"))));
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "3000");
        properties.put("mail.smtp.writetimeout", "5000");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(configuration.get("smtpFromAddress").toString());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
