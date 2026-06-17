package com.electrahub.notification.web;

import com.electrahub.notification.service.NotificationDtos;
import com.electrahub.notification.service.NotificationOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
    private final NotificationOrchestrator orchestrator;

    public NotificationController(NotificationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/notifications")
    public ResponseEntity<List<NotificationDtos.NotificationResponse>> submit(@Valid @RequestBody NotificationDtos.SubmitNotificationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(orchestrator.submit(request));
    }

    @GetMapping("/inbox")
    public List<NotificationDtos.NotificationResponse> inbox(@RequestParam String recipientRef) {
        return orchestrator.inbox(recipientRef);
    }

    @PatchMapping("/inbox/{id}/read")
    public NotificationDtos.NotificationResponse markRead(@PathVariable UUID id, @RequestParam String recipientRef) {
        return orchestrator.markRead(id, recipientRef);
    }

    @PostMapping("/contacts")
    public ResponseEntity<NotificationDtos.ContactResponse> registerContact(@Valid @RequestBody NotificationDtos.ContactRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orchestrator.registerContact(request));
    }

    @PostMapping("/contact")
    public ResponseEntity<NotificationDtos.AcceptedResponse> contact(@Valid @RequestBody NotificationDtos.ContactSubmissionRequest request, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(orchestrator.submitContact(request, clientIp(servletRequest)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
