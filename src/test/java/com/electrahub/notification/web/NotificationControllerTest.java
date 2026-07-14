package com.electrahub.notification.web;

import com.electrahub.notification.service.NotificationDtos;
import com.electrahub.notification.service.NotificationOrchestrator;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationControllerTest {
    @Test
    void registrationUsesGatewayAuthenticatedIdentity() {
        NotificationOrchestrator orchestrator = mock(NotificationOrchestrator.class);
        NotificationController controller = new NotificationController(orchestrator);
        NotificationDtos.PushDeviceRegistrationRequest request =
                new NotificationDtos.PushDeviceRegistrationRequest("device-1", "ios", "token-1", "firebase");

        controller.registerPushDevice("trusted-tenant", "trusted-user", request);

        verify(orchestrator).registerPushDevice("trusted-tenant", "trusted-user", request);
    }
}
