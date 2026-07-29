# Communication Preferences and Email Verification

## Scope

This design adds user-controlled email notifications and secure email OTP
verification to the driver experience. It is intentionally split across the
services that own each concern:

- `notification-service` owns communication-channel preferences and enforces
  them when optional notifications are dispatched.
- `auth-service` owns reusable verification challenges and their security
  lifecycle.
- `user-service` remains the source of truth for `emailVerified` and gateway
  authorization policy.
- `driver-portal-ios` presents communication settings and email verification.

Push notification behavior is unchanged. Push remains enabled by default and
is not exposed in the first settings release.

## User Experience

The More tab contains a Communications entry. Its first channel is Email, with
a master switch and an Email receipts child switch. The model and API are
channel/topic based so additional channels and topics can be added without a
new persistence design.

Email settings are unavailable until the account email is verified. An inline
warning explains the requirement and offers a Verify email action. A password
registration also presents the verification flow after sign-in. Verification
is encouraged but does not block access to the application.

The verification flow sends a six-digit code, supports paste and resend, shows
the resend delay, and allows dismissal. The profile warning uses the same flow.

## Preference Model

`notification.communication_preferences` stores sparse overrides:

| Column | Purpose |
| --- | --- |
| `tenant_id`, `user_id` | Preference owner |
| `channel` | Extensible channel key, initially `EMAIL` |
| `topic` | `ALL` for the master switch; initially `RECEIPT` for the child switch |
| `enabled` | User override |

Missing rows resolve to enabled to preserve current delivery behavior. Effective
email delivery additionally requires a verified email from `user-service`.
Optional receipt events honor both `EMAIL/ALL` and `EMAIL/RECEIPT`. Security and
verification emails bypass optional preferences so a user cannot disable account
recovery or verification messages.

APIs:

- `GET /notifications/api/v1/me/communication-preferences`
- `PUT /notifications/api/v1/me/communication-preferences/email`

The read response includes channel availability, effective state, blocked
reason, and topic state. The update endpoint rejects changes while the email is
unverified. Identity is taken only from trusted gateway headers; the client
cannot select another user.

## OTP Challenge Model

`auth.verification_challenges` is purpose- and channel-neutral. The first values
are `EMAIL_VERIFICATION` and `EMAIL`; future values can support phone
verification, MFA, or passwordless login without changing the challenge
lifecycle.

Each challenge records the user, purpose, channel, masked destination,
destination hash, code digest, status, expiry, attempt count, and timestamps.
The clear OTP is never persisted. The digest is HMAC-SHA-256 over the challenge
identifier, purpose, channel, and code using a dedicated `AUTH_OTP_PEPPER`
secret. A database reader cannot practically test all six-digit values without
also compromising that secret.

Defaults:

- Six numeric digits generated with `SecureRandom`.
- Ten-minute expiry.
- Sixty-second resend cooldown.
- Five failed attempts before lockout.
- A new challenge supersedes all active challenges for the same user and
  purpose.
- Verification consumes the challenge atomically before marking the user email
  verified.

APIs:

- `GET /auth/api/auth/email-verification/otp/status`
- `POST /auth/api/auth/email-verification/otp/request`
- `POST /auth/api/auth/email-verification/otp/verify`

These endpoints require a valid bearer identity even though registration and
legacy authentication routes share the `/api/auth` path. Status lets the app
reuse the OTP automatically issued during registration instead of generating a
second message.

Legacy token verification remains available only for already-issued links.
New registration and resend requests issue OTP challenges.

## Sensitive Delivery

The OTP is transient in the auth-to-notification broker event and process
memory. Notification persistence contains only a redacted audit message and a
redacted payload; the normal asynchronous notification body is not used for OTP
delivery. Logs must never serialize the OTP event payload. Mobile HTTP logging
redacts code, password, refresh token, access token, and OAuth token fields.

## Event Flow

1. Password registration creates the user and access session.
2. Auth issues an `EMAIL_VERIFICATION` challenge and publishes
   `USER_EMAIL_OTP_REQUESTED` with the clear code for delivery only.
3. Notification-service writes a redacted idempotency/audit record and sends the
   transient email through the configured provider.
4. The app opens verification status, accepts the code, and submits it with the
   authenticated user identity.
5. Auth validates expiry and attempts with a constant-time digest comparison,
   consumes the challenge, and marks the user verified in user-service.
6. Email preferences become available on the next settings/profile refresh.

## Rollout and Operations

1. Apply auth, notification, and user-policy Liquibase changes.
2. Provision a random `AUTH_OTP_PEPPER` through the production secret store.
3. Deploy user-service policy, notification-service, and auth-service.
4. Verify SMTP delivery, redacted database rows, cooldown, expiry, lockout, and
   registration behavior.
5. Release the iOS client after backend compatibility is confirmed.

Operational metrics should distinguish issued, delivered, verified, expired,
locked, and provider-failed challenges without including destination or OTP
values. Alerts should cover sustained provider failures and unusual challenge
issuance or failure rates.
