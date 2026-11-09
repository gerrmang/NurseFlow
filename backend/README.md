# NurseFlow Backend

Kotlin + Spring Boot API for drafting, manually editing, validating and publishing a ward roster. H2 stores local data in `backend/nurseflow.mv.db`.

## Configuration and provisioning

Java 21 is required. Set these environment variables in the run configuration (never commit their values):

| Variable | Purpose |
| --- | --- |
| `NURSEFLOW_SESSION_SECRET` | Required. HMAC key (32+ bytes) for NurseFlow access tokens. Startup fails without it. |
| `NURSEFLOW_KAKAO_AUDIENCES` | Comma-separated Kakao app keys accepted as the ID token `aud` (Android native app key, web key). Empty → Kakao login returns 503 `IDENTITY_PROVIDER_NOT_CONFIGURED`. |
| `NURSEFLOW_KAKAO_REST_API_KEY` | Web login only. Kakao REST API key: `client_id` for the code exchange and the only accepted ID token `aud` on the web path. Empty → web login returns 503 `IDENTITY_PROVIDER_NOT_CONFIGURED`. |
| `NURSEFLOW_KAKAO_CLIENT_SECRET` | Optional, web login. Set only if Client Secret is enabled for the Kakao app. Never commit it. |
| `NURSEFLOW_KAKAO_REDIRECT_URIS` | Web login. Comma-separated **exact** callback URIs (for example `http://localhost:5173/auth/kakao/callback`), also registered in Kakao Developers. |
| `NURSEFLOW_WEB_ALLOWED_ORIGINS` | Optional. Comma-separated `Origin` values allowed on `/api/v1/auth/web/*`. Empty → the origins of the redirect URIs. |
| `NURSEFLOW_WEB_COOKIE_SECURE` | Optional, default `true`. Keep `true`; browsers treat `http://localhost` as secure. |
| `NURSEFLOW_BOOTSTRAP_CODE` | Optional, demo only. Enables `POST /api/v1/wards` so the first signed-in user can create a ward and become `WARD_ADMIN`. Leave empty otherwise. |
| `NURSEFLOW_HOSPITAL_API_KEY` | Optional. data.go.kr key for 건강보험심사평가원 병원정보서비스 (the Decoding or Encoding form both work). Enables `GET /api/v1/hospitals/search`; empty → 503 `HOSPITAL_SEARCH_NOT_CONFIGURED`. Never commit it. |
| `NURSEFLOW_INVITE_LINK_BASE` | Optional. Invitation page URL; responses then include `inviteUrl`. |

### Sign-in and session

1. Android (Kakao SDK) obtains a Kakao **OIDC ID token** (enable OpenID Connect in Kakao Developers).
2. `POST /api/v1/auth/kakao/session {idToken, nonce?}` verifies it against Kakao's JWKS, issuer, audience, expiry and issue time, creates or finds the `users` row, and returns a NurseFlow access token (10 min) and refresh token (30 days).
3. Every other API call uses `Authorization: Bearer <NurseFlow access token>`. Kakao tokens are not accepted as API tokens.
4. `POST /api/v1/auth/session/refresh` keeps the user signed in by rotating the refresh token; reuse of a rotated token revokes the whole session. `POST /api/v1/auth/logout` revokes it (idempotent). Only SHA-256 hashes of refresh tokens are stored.
5. `GET /api/v1/me` and `GET /api/v1/wards` return the user and server-side ward memberships. Signing in alone grants no ward access.

### Web login (authorization code, HttpOnly cookie)

The web app does not handle Kakao tokens or the refresh token. Every `/api/v1/auth/web/*` call must send `X-NurseFlow-Client: web`; a browser `Origin` must be an allowed origin (CSRF defence). The web app is expected to call the API from the same site (for example through the Vite dev proxy or one reverse proxy), because the cookies are `SameSite=Strict`.

1. `POST /api/v1/auth/web/kakao/authorization {redirectUri}` → `{authorizationUrl, expiresAt}` and an HttpOnly `nf_kakao_login` cookie. The server generates and stores `state` and `nonce` (10 minutes, single use) and the web app simply navigates to `authorizationUrl`.
2. Kakao redirects to the callback with `code` and `state`. `POST /api/v1/auth/web/kakao/code {code, state}` checks that the state belongs to this browser (cookie), is unexpired and unused, consumes it, exchanges the code at Kakao's token endpoint with the stored redirect URI, and verifies the ID token (`aud` = REST API key, `nonce` = stored value). The response has the access token only; the refresh token is set as `nf_refresh` (HttpOnly, Secure, SameSite=Strict, Path=/api/v1/auth/web).
3. `POST /api/v1/auth/web/session/refresh` (no body) rotates the cookie with the same reuse detection as Android. `POST /api/v1/auth/web/logout` (no body) revokes the session and clears the cookie.

The Android routes above are unchanged and never set cookies.

Tests use an in-process fake OIDC provider (`src/test/kotlin/com/nurseflow/support/FakeOidcProvider.kt`) instead of Kakao. Real Kakao end-to-end login has not been verified until the Kakao Developers app, keys and redirect URIs are registered.

### Invitations

A `WARD_ADMIN` issues invitations with `POST /api/v1/wards/{wardId}/invitations {role, nurseId?, expiresInHours}` (1–168 h, default 72). The response contains the 43-character token **once**; only its SHA-256 hash is stored. `GET` lists invitations with status (`PENDING`/`REDEEMED`/`REVOKED`/`EXPIRED`) and no tokens, and `DELETE .../{invitationId}` revokes an unused invitation. Set `NURSEFLOW_INVITE_LINK_BASE` (for example `https://<web-host>/invite`) to also receive `inviteUrl`, with the token in the URL fragment. `WARD_ADMIN` invitations cannot link a nurse record because administrators are excluded from rostering. Creation and revocation are written to `audit_events`.

The invitee signs in with Kakao, opens the link, and calls `POST /api/v1/invitations/preview {token}` to see the ward and role, then `POST /api/v1/invitations/redeem {token}`. Redemption consumes the invitation, creates the ward membership and, for `WARD_MEMBER`, links the invited nurse record (or creates one with the user's display name) in one transaction. The role always comes from the invitation; extra fields such as `role` are rejected. A user who already belongs to the ward gets `409 ALREADY_WARD_MEMBER` and the invitation stays usable. Role changes for existing members are not implemented yet.

WARD_ADMIN invitation redemption returns 409 `ADMIN_NURSE_RECORD_CONFLICT` if the user has an active nurse record in that ward. This keeps managers out of the roster population without silently deactivating existing schedule data.

| Role | Access |
| --- | --- |
| Signed in, no membership | `/me`, `/wards` (empty), invitation preview/redeem. Every ward endpoint returns `404 WARD_NOT_FOUND`. |
| `WARD_MEMBER` | Published ward calendar and own published calendar. Drafts, absences, generation, confirmation and invitations return `403 WARD_ADMIN_REQUIRED`. |
| `WARD_ADMIN` | Everything above plus drafts, absences, assignments, generation, confirmation and invitations. Not part of the rostered nurse pool. |

### Teams and career profiles

- Teams: `GET/POST /api/v1/wards/{wardId}/teams`, `PUT/DELETE .../teams/{teamId}`. Members read; admins change. A team with active nurses cannot be deleted.
- Career policy: `GET/PUT /api/v1/wards/{wardId}/career-policy`. `stageThresholdMonths` are the lower bounds of stages 2–5 in months; the default `[12, 36, 72, 120]` gives <1y = 1, 1–3y = 2, 3–6y = 3, 6–10y = 4, 10y+ = 5. `ranks` default to 팀장·과장·신규 and are names only (they do not change the stage yet). Changes apply immediately.
- A `WARD_MEMBER` must provide the month they joined the hospital with `POST /api/v1/invitations/redeem {token, hospitalJoinedOn}`. `PUT /api/v1/wards/{wardId}/my-profile {hospitalJoinedOn}` updates only that date.
- Admins set the actual career start month and rank with `PUT .../staff/{nurseId}/profile`. These values are stored separately from the hospital joining month; `scoringStage` uses the admin-confirmed career profile.
- The current staff list sorts by confirmed career start month. Admin drag-and-drop ordering within a team is not implemented yet. When added, that saved order will determine relative seniority even when it differs from hospital joining dates.
- `GET /api/v1/wards/{wardId}/staff` (admin) lists nurses by team order, then most experienced first, then name. `POST`/`PUT .../staff` create fictional nurse records and change name, qualifications or `active`; `PUT .../staff/{nurseId}/team` assigns a team. Nurses cannot change their own team.

The service cannot serve scheduling requests until `shift_types` has been provisioned in the local H2 database. Provisioning endpoints and production database migration are separate work.

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

Writes require the current strong `If-Match` ETag. Mutations that require `Idempotency-Key` retain their original response in H2, so a retry with the same key and payload returns the prior result. The generator is a heuristic and does not claim optimality. Membership role changes, shift-type configuration APIs, Android/web API integration, production database migrations, and full contract coverage remain to be built; see the [MVP scope](../docs/mvp-scope.md).

## Build

Open this directory as an existing Gradle project in IntelliJ IDEA and select JDK 21. Run `./gradlew test` from `backend/` for the local checks. To start the API, provide the environment variables above, then run `./gradlew bootRun`. The API has no anonymous development bypass, and ward data must be provisioned before scheduling requests can succeed.
