package com.electrahub.notification.web;

import com.electrahub.notification.service.CommunicationPreferenceService;
import com.electrahub.notification.service.NotificationDtos;
import com.electrahub.notification.service.InboxRealtimeService;
import com.electrahub.notification.service.NotificationOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
    static final String AUTHENTICATED_USER_HEADER = "X-ElectraHub-User-Id";
    static final String AUTHENTICATED_TENANT_HEADER = "X-ElectraHub-Tenant-Id";

    private final NotificationOrchestrator orchestrator;
    private final InboxRealtimeService inboxRealtimeService;
    private final CommunicationPreferenceService communicationPreferenceService;

    public NotificationController(
            NotificationOrchestrator orchestrator,
            InboxRealtimeService inboxRealtimeService,
            CommunicationPreferenceService communicationPreferenceService
    ) {
        this.orchestrator = orchestrator;
        this.inboxRealtimeService = inboxRealtimeService;
        this.communicationPreferenceService = communicationPreferenceService;
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

    @GetMapping("/me/inbox")
    public NotificationDtos.InboxPageResponse myInbox(
            @RequestHeader(AUTHENTICATED_TENANT_HEADER) String tenantId,
            @RequestHeader(AUTHENTICATED_USER_HEADER) String userId,
            @RequestParam(defaultValue = "all") String state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return orchestrator.inboxForUser(
                tenantId,
                userId,
                NotificationDtos.InboxReadState.from(state),
                page,
                size
        );
    }

    @PatchMapping("/me/inbox/{id}/read")
    public NotificationDtos.NotificationResponse markMyNotificationRead(
            @PathVariable UUID id,
            @RequestHeader(AUTHENTICATED_TENANT_HEADER) String tenantId,
            @RequestHeader(AUTHENTICATED_USER_HEADER) String userId
    ) {
        return orchestrator.setInboxReadState(id, tenantId, userId, true);
    }

    @PatchMapping("/me/inbox/{id}/unread")
    public NotificationDtos.NotificationResponse markMyNotificationUnread(
            @PathVariable UUID id,
            @RequestHeader(AUTHENTICATED_TENANT_HEADER) String tenantId,
            @RequestHeader(AUTHENTICATED_USER_HEADER) String userId
    ) {
        return orchestrator.setInboxReadState(id, tenantId, userId, false);
    }

    @PatchMapping("/me/inbox/read-all")
    public NotificationDtos.InboxMutationResponse markMyInboxRead(
            @RequestHeader(AUTHENTICATED_TENANT_HEADER) String tenantId,
            @RequestHeader(AUTHENTICATED_USER_HEADER) String userId
    ) {
        return orchestrator.markAllInboxRead(tenantId, userId);
    }

    @GetMapping(value = "/me/inbox/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMyInbox(
            @RequestHeader(AUTHENTICATED_TENANT_HEADER) String tenantId,
            @RequestHeader(AUTHENTICATED_USER_HEADER) String userId
    ) {
        return inboxRealtimeService.connect(tenantId, userId);
    }

    @GetMapping("/me/communication-preferences")
    public NotificationDtos.CommunicationPreferencesResponse communicationPreferences(
            @RequestHeader(AUTHENTICATED_TENANT_HEADER) String tenantId,
            @RequestHeader(AUTHENTICATED_USER_HEADER) String userId
    ) {
        return communicationPreferenceService.get(tenantId, userId);
    }

    @PutMapping("/me/communication-preferences/email")
    public NotificationDtos.CommunicationPreferencesResponse updateEmailPreferences(
            @RequestHeader(AUTHENTICATED_TENANT_HEADER) String tenantId,
            @RequestHeader(AUTHENTICATED_USER_HEADER) String userId,
            @Valid @RequestBody NotificationDtos.UpdateEmailPreferencesRequest request
    ) {
        return communicationPreferenceService.updateEmail(tenantId, userId, request);
    }

    @PostMapping("/contacts")
    public ResponseEntity<NotificationDtos.ContactResponse> registerContact(@Valid @RequestBody NotificationDtos.ContactRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orchestrator.registerContact(request));
    }

    @PostMapping("/push/devices")
    public ResponseEntity<NotificationDtos.PushDeviceResponse> registerPushDevice(
            @RequestHeader(AUTHENTICATED_TENANT_HEADER) String tenantId,
            @RequestHeader(AUTHENTICATED_USER_HEADER) String userId,
            @Valid @RequestBody NotificationDtos.PushDeviceRegistrationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orchestrator.registerPushDevice(tenantId, userId, request));
    }

    @DeleteMapping("/push/devices/{deviceId}")
    public ResponseEntity<Void> unregisterPushDevice(
            @PathVariable String deviceId,
            @RequestHeader(AUTHENTICATED_TENANT_HEADER) String tenantId,
            @RequestHeader(AUTHENTICATED_USER_HEADER) String userId,
            @RequestParam(defaultValue = "firebase") String provider
    ) {
        orchestrator.unregisterPushDevice(tenantId, userId, provider, deviceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contact")
    public ResponseEntity<NotificationDtos.AcceptedResponse> contact(@Valid @RequestBody NotificationDtos.ContactSubmissionRequest request, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(orchestrator.submitContact(request, clientIp(servletRequest)));
    }

    @PostMapping("/public/contact-inquiries")
    public ResponseEntity<NotificationDtos.AcceptedResponse> projectBrief(
            @Valid @RequestBody NotificationDtos.ProjectBriefSubmissionRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(orchestrator.submitProjectBrief(request, clientIp(servletRequest)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
