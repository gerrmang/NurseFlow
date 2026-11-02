# NurseFlow Backend

Kotlin + Spring Boot API for drafting, manually editing, validating and publishing a ward roster. H2 stores local data in `backend/nurseflow.mv.db`.

## Configuration and provisioning

Java 21 is required. Set these environment variables in the run configuration (never commit their values):

| Variable | Purpose |
| --- | --- |
| `NURSEFLOW_SESSION_SECRET` | Required. HMAC key (32+ bytes) for NurseFlow access tokens. Startup fails without it. |
| `NURSEFLOW_KAKAO_AUDIENCES` | Comma-separated Kakao app keys accepted as the ID token `aud` (Android native app key, web key). Empty → Kakao login returns 503 `IDENTITY_PROVIDER_NOT_CONFIGURED`. |
| `NURSEFLOW_BOOTSTRAP_CODE` | Optional, demo only. Enables `POST /api/v1/wards` so the first signed-in user can create a ward and become `WARD_ADMIN`. Leave empty otherwise. |
| `NURSEFLOW_INVITE_LINK_BASE` | Optional. Invitation page URL; responses then include `inviteUrl`. |

### Sign-in and session

1. Android (Kakao SDK) or web (Kakao login) obtains a Kakao **OIDC ID token** (enable OpenID Connect in Kakao Developers).
2. `POST /api/v1/auth/kakao/session {idToken, nonce?}` verifies it against Kakao's JWKS, issuer, audience, expiry and issue time, creates or finds the `users` row, and returns a NurseFlow access token (10 min) and refresh token (30 days).
3. Every other API call uses `Authorization: Bearer <NurseFlow access token>`. Kakao tokens are not accepted as API tokens.
4. `POST /api/v1/auth/session/refresh` keeps the user signed in by rotating the refresh token; reuse of a rotated token revokes the whole session. `POST /api/v1/auth/logout` revokes it (idempotent). Only SHA-256 hashes of refresh tokens are stored.
5. `GET /api/v1/me` and `GET /api/v1/wards` return the user and server-side ward memberships. Signing in alone grants no ward access.

Tests use an in-process fake OIDC provider (`src/test/kotlin/com/nurseflow/support/FakeOidcProvider.kt`) instead of Kakao. Real Kakao end-to-end login has not been verified until the Kakao Developers app, keys and redirect URIs are registered.

### Invitations

A `WARD_ADMIN` issues invitations with `POST /api/v1/wards/{wardId}/invitations {role, nurseId?, expiresInHours}` (1–168 h, default 72). The response contains the 43-character token **once**; only its SHA-256 hash is stored. `GET` lists invitations with status (`PENDING`/`REDEEMED`/`REVOKED`/`EXPIRED`) and no tokens, and `DELETE .../{invitationId}` revokes an unused invitation. Set `NURSEFLOW_INVITE_LINK_BASE` (for example `https://<web-host>/invite`) to also receive `inviteUrl`, with the token in the URL fragment. `WARD_ADMIN` invitations cannot link a nurse record because administrators are excluded from rostering. Creation and revocation are written to `audit_events`.

The service cannot serve scheduling requests until `nurses` and `shift_types` have been provisioned in the local H2 database. Provisioning endpoints and production database migration are separate work.

`ward_memberships.subject` and `nurses.subject` hold the NurseFlow `userId` (the access token `sub`). Administrators need a `WARD_ADMIN` membership. The whole ward calendar also requires `can_view_ward_calendar=TRUE`. The personal calendar resolves its nurse ID from the authenticated subject and never accepts a nurse ID in the request.

The API stores approved absence category and time range only. It does not store leave reasons or clinical details. Published calendar responses omit absence records and use `Cache-Control: private, no-store`.

## Implemented flow

1. `POST /api/v1/wards/{wardId}/rosters` creates an empty draft for a month.
2. `PUT /api/v1/rosters/{rosterId}/requirements` creates slots from configured shift types.
3. `POST /api/v1/rosters/{rosterId}/absences` records approved leave or half-day intervals.
4. `POST /api/v1/rosters/{rosterId}/assignments/validate` returns hard-rule reasons before placement.
5. `PUT /api/v1/rosters/{rosterId}/slots/{slotId}/assignment` rechecks hard rules on the server and rejects invalid placement.
6. `POST /api/v1/rosters/{rosterId}/generation-jobs` starts a bounded deterministic draft-generation job. Status and cancellation endpoints report completed, partial, timed-out, cancelled, or failed outcomes; unfilled slots remain visible.
7. `POST /api/v1/rosters/{rosterId}/confirm` revalidates the draft and atomically switches the active published calendar. An unfilled roster requires an exact `acknowledgeUnfilledCount`.
8. Published ward and personal calendars read only the active published roster. A new draft stays private until confirmed.

Writes require the current strong `If-Match` ETag. Mutations that require `Idempotency-Key` retain their original response in H2, so a retry with the same key and payload returns the prior result. The generator is a heuristic and does not claim optimality. Membership role changes, staff/shift configuration APIs, Android/web API integration, production database migrations, and full contract coverage remain to be built; see the [MVP scope](../docs/mvp-scope.md).

## Build

Open this directory as an existing Gradle project in IntelliJ IDEA and select JDK 21. Run `./gradlew test` from `backend/` for the local checks. To start the API, provide the environment variables above, then run `./gradlew bootRun`. The API has no anonymous development bypass, and ward data must be provisioned before scheduling requests can succeed.
