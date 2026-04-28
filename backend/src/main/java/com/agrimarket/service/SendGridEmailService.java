package com.agrimarket.service;

import com.agrimarket.config.EmailProperties;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SendGridEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailService.class);
    private final EmailProperties props;

    @Override
    public void send(String to, String subject, String plainTextBody, String htmlBody) {
        if (to == null || to.isBlank()) return;

        // In local/dev we often run without configured SendGrid.
        if (!props.isEnabled()) {
            log.info("[email disabled] to={} subject={}\n{}", to, subject, plainTextBody);
            return;
        }

        String apiKey = props.getSendgridApiKey() == null ? "" : props.getSendgridApiKey().trim();
        String from = props.getFrom() == null ? "" : props.getFrom().trim();
        if (apiKey.isEmpty() || from.isEmpty()) {
            log.warn("[email misconfigured] enabled=true but missing apiKey/from. to={} subject={}", to, subject);
            return;
        }

        try {
            Email fromEmail = new Email(from);
            Email toEmail = new Email(to.trim());
            String plain = plainTextBody == null ? "" : plainTextBody;
            String html = htmlBody == null ? "" : htmlBody;

            Mail mail = new Mail();
            mail.setFrom(fromEmail);
            mail.setSubject(subject == null ? "" : subject);
            mail.addContent(new Content("text/plain", plain));
            if (!html.isBlank()) {
                mail.addContent(new Content("text/html", html));
            }

            Personalization personalization = new Personalization();
            personalization.addTo(toEmail);
            mail.addPersonalization(personalization);

            SendGrid sg = new SendGrid(apiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);
            int code = response.getStatusCode();
            if (code < 200 || code >= 300) {
                log.warn("SendGrid send failed: status={} body={}", code, response.getBody());
            }
        } catch (Exception e) {
            // Never break the main business flow due to email delivery.
            log.warn("SendGrid send error: {}", e.getMessage());
        }
    }
}

