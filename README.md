# Notification Service

Production-grade ElectraHub notification service for asynchronous email, SMS, push, and in-app notification workflows.

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
C:\tools\apache-maven-3.9.16\bin\mvn.cmd test package
```

## Docker

```powershell
docker buildx build --platform linux/amd64,linux/arm64 -t amolsurjuse/notification-service:<tag> --push .
```

## Runtime

The service uses:

- Postgres schema `notification`
- RabbitMQ queue `notifications.dispatch`
- Context path `/notifications`
- Real provider sending disabled by default until credentials and sandbox tests are complete

Email quota controls:

| Setting | Default | Purpose |
| --- | --- | --- |
| `NOTIFICATION_CHANNEL_EMAIL_REAL_SEND_ENABLED` | `false` | Keeps email delivery disabled unless explicitly enabled. |
| `NOTIFICATION_CHANNEL_EMAIL_RATE_PER_SECOND` | `1` | Limits email dispatch spacing to one message per second. |
| `NOTIFICATION_CHANNEL_EMAIL_DAILY_LIMIT` | `59` | Rolling 24-hour app-side cap. Dev uses `59`, prod uses `150`. |
| `NOTIFICATION_CHANNEL_EMAIL_RETRY_ENABLED` | `false` | Documents the no-retry policy; failed email dispatches are marked failed and ignored. |

The daily cap is evaluated over the previous 24 hours from the notification database, so pod restarts do not reset the quota. Failed or disabled email dispatches are not requeued by the Rabbit listener.

Firebase push notification controls:

| Setting | Default | Purpose |
| --- | --- | --- |
| `NOTIFICATION_CHANNEL_PUSH_REAL_SEND_ENABLED` | `false` | Keeps Firebase Cloud Messaging disabled unless explicitly enabled. |
| `NOTIFICATION_CHANNEL_PUSH_RATE_PER_SECOND` | `5` | Limits push dispatch spacing to avoid accidental bursts. |
| `NOTIFICATION_CHANNEL_PUSH_DAILY_LIMIT` | `500` | Rolling 24-hour app-side cap. |
| `FIREBASE_PROJECT_ID` | empty | Optional Firebase project id. |
| `FIREBASE_SERVICE_ACCOUNT_PATH` | empty | Path to a mounted Firebase service account JSON file. |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | empty | Raw service account JSON for local runs only. Prefer a mounted secret in Kubernetes. |
| `FIREBASE_SERVICE_ACCOUNT_BASE64` | empty | Base64-encoded service account JSON for secret-based deployments. |

Push requests use channel `PUSH`. The adapter reads the device token from payload keys `fcmToken`, `deviceToken`, `pushToken`, or `registrationToken`; `recipientRef` is used only as a fallback token for internal callers. Provider failures are marked failed and ignored without retry.

Example:

```json
{
  "tenantId": "electrahub",
  "eventId": "session-started-123",
  "recipientRef": "user-123",
  "channels": ["PUSH"],
  "templateId": "session-started",
  "subject": "Charging started",
  "body": "Your charging session has started.",
  "payload": {
    "fcmToken": "<device-fcm-token>",
    "sessionId": "session-123"
  }
}
```
