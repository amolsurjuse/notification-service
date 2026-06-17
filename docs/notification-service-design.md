# ElectraHub Notification Service

The notification service owns asynchronous notification intake, idempotency, dispatch state, user contact registration, in-app inbox APIs, and provider webhook audit.

## Channels

- `IN_APP`: persisted inbox notifications.
- `EMAIL`: queued through the channel adapter registry. Current production deployment uses a no-op adapter until SES/SendGrid secrets and sandbox validation are complete.
- `SMS`: queued through the channel adapter registry. Current production deployment uses a no-op adapter until Twilio secrets and compliance setup are complete.
- `PUSH`: queued through the channel adapter registry. Current production deployment uses a no-op adapter until APNs/FCM credentials are delivered through External Secrets.

## Security

- Raw contact destinations are not stored in `user_contacts`; only salted SHA-256 hashes and masked values are stored.
- Actuator exposes only health, info, and Prometheus.
- Provider webhooks require a signature-like header before mutation; unsigned webhooks are audited and rejected.
- Provider secrets must be mounted through Kubernetes secrets or External Secrets, never committed.

## Public APIs

- `POST /notifications/api/v1/notifications`
- `GET /notifications/api/v1/inbox?recipientRef=...`
- `PATCH /notifications/api/v1/inbox/{id}/read?recipientRef=...`
- `POST /notifications/api/v1/contacts`
- `POST /notifications/api/v1/contact`
- `POST /notifications/webhooks/{provider}`
