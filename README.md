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
