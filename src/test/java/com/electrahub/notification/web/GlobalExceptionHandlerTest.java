package com.electrahub.notification.web;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final ExceptionHandlerMethodResolver resolver =
            new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

    @Test
    void disconnectedClientUsesNoBodyHandler() {
        Method handler = resolver.resolveMethod(
                new AsyncRequestNotUsableException("Servlet container error notification for disconnected client")
        );

        assertThat(handler).isNotNull();
        assertThat(handler.getName()).isEqualTo("asyncRequestClosed");
        assertThat(handler.getReturnType()).isEqualTo(Void.TYPE);
    }

    @Test
    void asyncTimeoutUsesNoBodyHandler() {
        Method handler = resolver.resolveMethod(new AsyncRequestTimeoutException());

        assertThat(handler).isNotNull();
        assertThat(handler.getName()).isEqualTo("asyncRequestClosed");
        assertThat(handler.getReturnType()).isEqualTo(Void.TYPE);
    }
}
