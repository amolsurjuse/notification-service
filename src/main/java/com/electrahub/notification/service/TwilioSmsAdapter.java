package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

public class TwilioSmsAdapter implements ChannelAdapter {
    private final boolean enabled;
    private final String accountSid;
    private final String fromNumber;
    private final RestClient client;

    public TwilioSmsAdapter(boolean enabled, String accountSid, String authToken, String fromNumber) {
        this.enabled = enabled;
        this.accountSid = text(accountSid);
        this.fromNumber = text(fromNumber);
        String token = text(authToken);
        if (enabled && (this.accountSid == null || token == null || this.fromNumber == null)) {
            throw new IllegalStateException("SMS delivery is enabled but Twilio credentials are incomplete");
        }
        String basic = Base64.getEncoder().encodeToString(((this.accountSid == null ? "" : this.accountSid)
                + ":" + (token == null ? "" : token)).getBytes(StandardCharsets.UTF_8));
        this.client = RestClient.builder().baseUrl("https://api.twilio.com")
                .defaultHeader("Authorization", "Basic " + basic).build();
    }

    @Override public Channel channel() { return Channel.SMS; }

    @Override
    public DispatchResult dispatch(NotificationMessage message) {
        if (!enabled) return DispatchResult.skipped("twilio", "SMS delivery is disabled");
        LinkedMultiValueMap<String,String> form = new LinkedMultiValueMap<>();
        form.add("To", message.getRecipientRef());
        form.add("From", fromNumber);
        form.add("Body", message.getBody());
        try {
            @SuppressWarnings("unchecked")
            Map<String,Object> response = client.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
            Object sid = response == null ? null : response.get("sid");
            return sid == null ? DispatchResult.failure("twilio", "Twilio response did not include a message id")
                    : DispatchResult.success("twilio", sid.toString());
        } catch (HttpServerErrorException exception) {
            return DispatchResult.retryableFailure("twilio", "Twilio temporarily unavailable");
        } catch (RuntimeException exception) {
            return DispatchResult.failure("twilio", "Twilio rejected the SMS request");
        }
    }

    private static String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
