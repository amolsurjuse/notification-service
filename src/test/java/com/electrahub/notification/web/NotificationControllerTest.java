package com.electrahub.notification.web;

import com.electrahub.notification.service.CommunicationPreferenceService;
import com.electrahub.notification.service.NotificationDtos;
import com.electrahub.notification.service.NotificationOrchestrator;
import com.electrahub.notification.service.InboxRealtimeService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationControllerTest {
    @Test
    void registrationUsesGatewayAuthenticatedIdentity() {
        NotificationOrchestrator orchestrator = mock(NotificationOrchestrator.class);
        NotificationController controller = new NotificationController(
                orchestrator, mock(InboxRealtimeService.class), mock(CommunicationPreferenceService.class));
        NotificationDtos.PushDeviceRegistrationRequest request =
                new NotificationDtos.PushDeviceRegistrationRequest("device-1", "ios", "token-1", "firebase");

        controller.registerPushDevice("trusted-tenant", "trusted-user", request);

        verify(orchestrator).registerPushDevice("trusted-tenant", "trusted-user", request);
    }

    @Test
    void driverInboxUsesGatewayAuthenticatedIdentity() {
        NotificationOrchestrator orchestrator = mock(NotificationOrchestrator.class);
        NotificationController controller = new NotificationController(
                orchestrator, mock(InboxRealtimeService.class), mock(CommunicationPreferenceService.class));

        controller.myInbox("trusted-tenant", "trusted-user", "unread", 1, 25);

        verify(orchestrator).inboxForUser(
                "trusted-tenant",
                "trusted-user",
                NotificationDtos.InboxReadState.UNREAD,
                1,
                25
        );
    }

    @Test
    void communicationPreferenceUpdateUsesGatewayAuthenticatedIdentity() {
        NotificationOrchestrator orchestrator = mock(NotificationOrchestrator.class);
        CommunicationPreferenceService preferenceService = mock(CommunicationPreferenceService.class);
        NotificationController controller = new NotificationController(
                orchestrator, mock(InboxRealtimeService.class), preferenceService);
        NotificationDtos.UpdateEmailPreferencesRequest request =
                new NotificationDtos.UpdateEmailPreferencesRequest(false, true);

        controller.updateEmailPreferences("trusted-tenant", "trusted-user", request);

        verify(preferenceService).updateEmail("trusted-tenant", "trusted-user", request);
    }
}
