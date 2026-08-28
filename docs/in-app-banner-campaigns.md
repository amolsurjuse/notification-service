# In-app banner campaigns

## Goal

Provide one backend-controlled campaign model for iOS, Android, web, admin portals, and future clients. Campaign content and delivery rules are data, while clients own native rendering.

## Delivery API

`GET /api/v1/me/banners?screen=SIGN_IN&platform=IOS&locale=en-NL`

The service resolves the authenticated user's audience profile server-side and returns only campaigns that match:

- active flag and `[startsAt, endsAt)` schedule
- screen placement and platform
- locale, with wildcard fallback
- included/excluded countries
- user registration window
- optional future tenant, role, app-version, and experiment rules

Results are ordered by priority and then most recently updated. API responses include a stable campaign key, content, style, action, dismissibility, schedule, and opaque metadata.

## Management API

Admin CRUD uses `/api/v1/admin/banner-campaigns`. Writes validate time windows, normalized target values, supported action URLs, and optimistic versioning. Deactivation is preferred to deletion for auditability.

## Client contract

Clients request banners using a stable screen name such as `SIGN_IN`, `DASHBOARD`, or `PAYMENTS`. A reusable native component renders the response and stores dismissals by campaign key plus content revision. Clients never decide country eligibility.

## Initial campaign

The first campaign announces the €200 welcome wallet credit to newly registered NL and DE users. Its targeting is campaign data, not application code. US, India, or Asian-market campaigns can reuse the same API with different country lists, schedules, screens, and localized content.

## Safety and operations

- Server time is authoritative.
- Inactive, future, and expired campaigns fail closed.
- Country and registration data come from user-service through an authenticated internal endpoint.
- Cache keys include campaign revision and audience dimensions; short TTLs allow rapid deactivation.
- Metrics cover requests, eligible campaigns, empty results, evaluation failures, and campaign impressions/clicks.
