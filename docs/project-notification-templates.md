# Project Notification Templates

## Decision

Notification presentation is project-specific and database-driven at runtime, but owned and reviewed in source control.

- Project branding and template metadata live in the notification database.
- Liquibase seeds every project and immutable template version from code-owned resources.
- The application loads enabled projects and templates into an in-memory catalog during startup.
- There is no runtime template upload or template administration API.
- Each notification type has a dedicated Java composer for validation, formatting, conditional sections, and attachments.
- Thymeleaf renders database-loaded subject and body templates with escaped values.

This model allows multiple products to share the notification service without allowing unreviewed HTML to enter production.

## Runtime flow

1. A domain event enters the notification service with a project key in `tenantId`, a `templateId`, channel, recipient, and payload.
2. `NotificationTemplateCatalog` selects the highest enabled version for project, template, channel, and locale, falling back to the project's default locale.
3. The registered `EmailComposer` validates and formats domain-specific payload data.
4. `NotificationTemplateRenderer` renders the dynamic subject and body.
5. The composer supplies inline resources and attachments. Receipt email generates a PDF in memory.
6. `SmtpEmailAdapter` creates a multipart MIME message with plain text, HTML, inline logo, and attachments.
7. The notification audit row records the project, template version, content type, rendered subject, and rendered body used for dispatch.

Template catalog loading is intentionally startup-only. A template release therefore follows the same review, migration, and deployment process as application code.

## Data model

### `notification_projects`

One row per product or brand:

| Column | Purpose |
| --- | --- |
| `project_key` | Stable lowercase identifier, also supplied as notification `tenantId`. |
| `display_name`, `legal_name` | User-facing and legal brand names. |
| `logo_resource` | Classpath path to a reviewed image asset. |
| `primary_color` | Brand color used by templates and generated documents. |
| `from_email`, `from_name`, `reply_to_email` | Project-specific email sender identity. |
| `support_email`, `website_url`, `business_address` | Footer and support data. |
| `default_locale`, `time_zone` | Formatting defaults. |
| `enabled` | Controls catalog availability. |

### `notification_templates`

One row per immutable project/template/channel/locale version:

| Column | Purpose |
| --- | --- |
| `project_key` | Owning project. |
| `template_key` | Domain event template identifier. |
| `channel` | `EMAIL` or `SMS`; the catalog is channel-neutral. |
| `locale` | BCP 47 language tag such as `en-US`. |
| `version` | Positive version number. The highest enabled version is selected. |
| `subject_template` | Dynamic subject text. |
| `body_template` | Dynamic HTML, text, or future SMS body. |
| `content_type` | Renderer and transport media type. |
| `enabled` | Controls whether the version participates in selection. |

The unique key is `project_key, template_key, channel, locale, version`.

## Code ownership

| Responsibility | Implementation |
| --- | --- |
| Startup catalog and validation | `NotificationTemplateCatalog` |
| Subject/body rendering | `NotificationTemplateRenderer` |
| Composer routing and limits | `EmailCompositionService` |
| Per-type receipt mapping | `ChargingReceiptEmailComposer` |
| Receipt view model | `ChargingReceiptEmailModel` |
| PDF generation | `ChargingReceiptPdfGenerator` |
| SMTP MIME transport | `SmtpEmailAdapter` |

Every new email type must register exactly one `EmailComposer`. The application rejects duplicate composers and fails composition when a database template has no code-owned composer.

SMS uses the same project/template/version/locale catalog contract. A real SMS template and composer should be added with the SMS provider adapter; the current receipt is email-only.

## Current template: charging receipt

| Property | Value |
| --- | --- |
| Project | `electrahub` |
| Template key | `charging-receipt-ready` |
| Channel | `EMAIL` |
| Locale | `en-US` |
| Version | `1` |
| Subject source | `src/main/resources/db/templates/electrahub/charging-receipt-email.subject.txt` |
| HTML source | `src/main/resources/db/templates/electrahub/charging-receipt-email.html` |
| Logo source | `src/main/resources/notification-assets/electrahub-logo.png` |

Subject:

```text
Your [[${projectDisplayName}]] charging receipt - [[${receiptNumber}]]
```

The HTML template includes project logo and title, station and connector, session timestamps and duration, energy, tariff, charging cost, conditional idle fee, conditional subscription discount and plan, taxes, total paid, payment method, status, support email, and business address.

The attached PDF contains the same normalized receipt model. Attachments are generated in memory and total attachment content is limited to 5 MB per notification.

## Receipt event contract

`CHARGING_RECEIPT_READY` supplies:

| Group | Fields |
| --- | --- |
| Identity | `sessionId`, `stationName`, `connectorLabel` |
| Timing | `startedAt`, `stoppedAt` |
| Usage | `energyKwh`, `tariffPerKwh` |
| Cost | `chargingCost`, `idleFee`, `idleFeePerMinute`, `idleSeconds`, `taxesUsd`, `totalCost`, `currency` |
| Payment | `paymentMethod`, `status` |
| Subscription | `regularCost`, `subscriptionDiscountAmount`, `subscriptionPlanCode`, `subscriptionPlanName`, `subscriptionQuotaUnit`, `subscriptionQuotaConsumed`, `subscriptionCoveredEnergyKwh`, `subscriptionUncoveredEnergyKwh`, `subscriptionQuotaExhausted` |

`ChargingReceiptEmailModel` owns formatting and defaults so raw payload values are never interpolated directly into transport logic.

## Adding a project

1. Add the reviewed project logo under `src/main/resources/notification-assets/<project>/`.
2. Add a new Liquibase changeset that inserts the `notification_projects` row.
3. Add project/template resource files under `src/main/resources/db/templates/<project>/`.
4. In the same or a later changeset, seed the first template version with `valueClobFile`.
5. Add or reuse a code-owned composer and test project selection, locale fallback, rendering, and assets.
6. Deploy the notification service. Startup loads and validates the new catalog snapshot.

## Updating a template

1. Do not edit an already deployed Liquibase changeset.
2. Add new subject/body resource files or a clearly versioned replacement.
3. Add a new changeset with the next `version`.
4. Keep the previous row for auditability. Disable it only when operationally required.
5. Update composer/view-model code when the data contract changes.
6. Update this document and the BookStack template page.
7. Run template catalog, renderer, composer, PDF, MIME, migration, and full service tests.
8. Deploy and confirm the startup log reports the expected project/template counts.
9. Trigger a non-production event and inspect the received email, PDF, and notification audit metadata.

## Security and operations

- Dynamic values are HTML-escaped by Thymeleaf.
- Logos are loaded only from reviewed classpath resources.
- Arbitrary runtime HTML and external asset URLs are not accepted.
- SMTP credentials remain Kubernetes secrets and never enter project/template rows.
- Attachment bytes are not persisted in the database.
- Invalid locale, timezone, missing logo, missing project, duplicate composer, or invalid template version fails fast.
- Provider failures retain the rendered template metadata on the notification record for investigation.
- Changing a database row directly is unsupported; publish a corrective Liquibase migration and restart the service.

## Verification

```powershell
docker run --rm -v C:\electrahub\notification-service-email-preferences-otp:/workspace -v C:\electrahub\m2-cache:/root/.m2 -w /workspace maven:3.9.11-eclipse-temurin-21 mvn -q test
```

Key tests:

- `NotificationTemplateMigrationTest`
- `NotificationTemplateCatalogTest`
- `NotificationTemplateRendererTest`
- `ChargingReceiptEmailComposerTest`
- `EmailCompositionServiceTest`
- `SmtpEmailAdapterTest`
