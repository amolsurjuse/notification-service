package com.electrahub.notification.web;

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
        NotificationController controller = new NotificationController(orchestrator, mock(InboxRealtimeService.class));
        NotificationDtos.PushDeviceRegistrationRequest request =
                new NotificationDtos.PushDeviceRegistrationRequest("device-1", "ios", "token-1", "firebase");

        controller.registerPushDevice("trusted-tenant", "trusted-user", request);

        verify(orchestrator).registerPushDevice("trusted-tenant", "trusted-user", request);
    }

    @Test
    void driverInboxUsesGatewayAuthenticatedIdentity() {
        NotificationOrchestrator orchestrator = mock(NotificationOrchestrator.class);
        NotificationController controller = new NotificationController(orchestrator, mock(InboxRealtimeService.class));

        controller.myInbox("trusted-tenant", "trusted-user", "unread", 1, 25);

        verify(orchestrator).inboxForUser(
                "trusted-tenant",
                "trusted-user",
                NotificationDtos.InboxReadState.UNREAD,
                1,
                25
        );
    }
}
