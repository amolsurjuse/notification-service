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
| `NOTIFICATION_CHANNEL_EMAIL_PROVIDER` | `smtp-email` | Provider label stored with dispatched messages. |
| `NOTIFICATION_CHANNEL_EMAIL_RATE_PER_SECOND` | `1` | Limits email dispatch spacing to one message per second. |
| `NOTIFICATION_CHANNEL_EMAIL_DAILY_LIMIT` | `59` | Rolling 24-hour app-side cap. Dev uses `59`, prod uses `150`. |
| `NOTIFICATION_CHANNEL_EMAIL_RETRY_ENABLED` | `false` | Documents the no-retry policy; failed email dispatches are marked failed and ignored. |
| `NOTIFICATION_EMAIL_SMTP_HOST` | empty | SMTP server host. Leave empty until credentials are ready. |
| `NOTIFICATION_EMAIL_SMTP_PORT` | `587` | SMTP submission port. |
| `NOTIFICATION_EMAIL_SMTP_USERNAME` | empty | SMTP username/API login. Store as a Kubernetes Secret. |
| `NOTIFICATION_EMAIL_SMTP_PASSWORD` | empty | SMTP password/API key. Store as a Kubernetes Secret. |
| `NOTIFICATION_EMAIL_SMTP_FROM_EMAIL` | `no-reply@electrahub.net` | Sender address used for outbound transactional email. |
| `NOTIFICATION_EMAIL_SMTP_FROM_NAME` | `ElectraHub` | Sender display name. |
| `NOTIFICATION_EMAIL_SMTP_STARTTLS_ENABLED` | `true` | Enables STARTTLS for port 587 providers. |
| `NOTIFICATION_EMAIL_SMTP_AUTH_ENABLED` | `true` | Enables SMTP authentication. |

The daily cap is evaluated over the previous 24 hours from the notification database, so pod restarts do not reset the quota. Failed or disabled email dispatches are not requeued by the Rabbit listener.

Email is delivered through a provider-neutral SMTP adapter. Disabled email and quota-exceeded email are recorded as `SKIPPED`; SMTP provider errors are recorded as `FAILED` and are not retried.

Recommended low-cost setup:

1. Use Cloudflare DNS/Email Routing for the domain and inbound aliases.
2. Use a transactional SMTP provider for outbound email. Brevo, Resend, or AWS SES are good starting options; Brevo's free quota is usually easiest for low-volume SMTP.
3. Verify `electrahub.net` or a sending subdomain in the provider, then add the provider's SPF/DKIM/DMARC DNS records in Cloudflare.
4. Create an SMTP/API key in the provider.
5. Store the SMTP credentials in Kubernetes, then enable real sending only after DNS verification is complete.

Example Kubernetes secret:

```powershell
wsl -d Ubuntu-24.04 -- kubectl -n dev create secret generic notification-email-secret --from-literal=NOTIFICATION_EMAIL_SMTP_USERNAME='<smtp-username>' --from-literal=NOTIFICATION_EMAIL_SMTP_PASSWORD='<smtp-password-or-api-key>' --dry-run=client -o yaml | wsl -d Ubuntu-24.04 -- kubectl apply -f -
wsl -d Ubuntu-24.04 -- kubectl -n prod create secret generic notification-email-secret --from-literal=NOTIFICATION_EMAIL_SMTP_USERNAME='<smtp-username>' --from-literal=NOTIFICATION_EMAIL_SMTP_PASSWORD='<smtp-password-or-api-key>' --dry-run=client -o yaml | wsl -d Ubuntu-24.04 -- kubectl apply -f -
```

After the secret exists, wire the secret into the Helm values and set:

```yaml
NOTIFICATION_CHANNEL_EMAIL_REAL_SEND_ENABLED: "true"
NOTIFICATION_EMAIL_SMTP_HOST: "smtp-relay.brevo.com"
NOTIFICATION_EMAIL_SMTP_PORT: "587"
```

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
