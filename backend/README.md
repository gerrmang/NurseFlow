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
| `NURSEFLOW_HOSPITAL_DATASET_PATH` | Optional. Path to a newer downloaded 「전국 병의원 및 약국 현황」 zip (data.go.kr dataset 15051059, updated quarterly) or its `병원정보서비스` CSV than the bundled list. Imported at startup when the file changed (about 80,000 rows in under a second); searches then run on this local copy in milliseconds. A file with fewer than 10,000 rows is rejected and the previous list is kept. |
| `NURSEFLOW_HOSPITAL_DATASET_BUNDLED` | Optional, default `true`. Without a dataset path, imports the hospital list shipped in the server (`src/main/resources/hospitals/`) at startup when it differs from the latest import, so a local server needs no download and a corrected list of the same period is applied. It is skipped when the latest import is of a later period, or when either period is unknown (a file name without `(YYYY.M.)`, such as `hospitals.csv`). After such an undated import, bundled updates stay off until a dated file is imported once through `NURSEFLOW_HOSPITAL_DATASET_PATH` (the zip as downloaded, or its CSV with the period kept in the name, e.g. `1.병원정보서비스(2026.9.).csv`). That file must differ in content from the undated one: a file whose content was already imported is skipped whatever its name, so renaming the same CSV does not recover; use the downloaded zip or a newer release. While a dataset path is set, only that file is used. |
| `NURSEFLOW_HOSPITAL_API_KEY` | Optional fallback. data.go.kr key for the live 병원정보서비스 API (Decoding or Encoding form), used only until a dataset is imported; each live search often takes 10+ seconds. Without a dataset and a key, search returns 503 `HOSPITAL_SEARCH_NOT_CONFIGURED`. Never commit it. |
| `NURSEFLOW_INVITE_LINK_BASE` | Optional. Invitation page URL; responses then include `inviteUrl`. |

The bundled list keeps only the five columns the server imports (institution id, name, category code and name, address) from the official file, gzip-compressed (about 2.5 MB; public institution data, no personal records). To refresh it after a quarterly release, download the zip and run `python3 -I backend/scripts/extract_hospital_list.py <zip>`, which replaces the file in `src/main/resources/hospitals/` only after the new list has at least 10,000 rows and is completely written; the full zip is never committed.

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

### Ward creation

A signed-in user creates a ward with `POST /api/v1/wards {hospital{source:"HIRA", externalId}, wardName, timeZone?}` and an `Idempotency-Key`, using a result of `GET /api/v1/hospitals/search`, and becomes its only `WARD_ADMIN` (no nurse record). The server looks the hospital up in the imported official list and stores that name and address with the ward, so a hospital that is not listed or was deactivated returns 422 `HOSPITAL_NOT_FOUND`; without an imported list it returns 503 `HOSPITAL_SEARCH_NOT_CONFIGURED`. Picking a hospital is not proof of employment: other admins may create wards with the same hospital and name, and none gains access to another's ward. `GET /api/v1/wards` and `/me` include `hospitalName`. Retrying with the same key returns the first ward. One account may create at most `NURSEFLOW_WARD_MAX_CREATED_PER_USER` wards (default 5; 409 `WARD_CREATION_LIMIT_REACHED`), and creating the same hospital and ward name twice from one account with a new key is rejected as a double submit (409 `WARD_ALREADY_CREATED`). The former demo bootstrap code (`NURSEFLOW_BOOTSTRAP_CODE`) is removed.

### Invitations

A `WARD_ADMIN` issues invitations with `POST /api/v1/wards/{wardId}/invitations {role, nurseId?}`. Every invitation is single-use and expires **24 hours** after issue (fixed; there is no `expiresInHours`, and unknown fields are rejected with 400). `POST .../{invitationId}/reissue` issues a new link with the same role and nurse target and revokes the previous unused (pending or expired) link; a redeemed or revoked invitation cannot be reissued. A nurse record can have only one pending invitation (`409 INVITATION_ALREADY_PENDING`). The response contains the 43-character token **once**; only its SHA-256 hash is stored. `GET` lists invitations with status (`PENDING`/`REDEEMED`/`REVOKED`/`EXPIRED`) and no tokens, and `DELETE .../{invitationId}` revokes an unused invitation. Set `NURSEFLOW_INVITE_LINK_BASE` (for example `https://<web-host>/invite`) to also receive `inviteUrl`, with the token in the URL fragment. `WARD_ADMIN` invitations cannot link a nurse record because administrators are excluded from rostering. Creation and revocation are written to `audit_events`.

The invitee signs in with Kakao, opens the link, and calls `POST /api/v1/invitations/preview {token}` to see the ward and role, then `POST /api/v1/invitations/redeem {token, hospitalJoinedOn}`. `WARD_MEMBER` invitees must send the month they joined this hospital (`YYYY-MM`; missing → 400 `HOSPITAL_JOIN_MONTH_REQUIRED`, a future month or before 1950 → 400 `INVALID_HOSPITAL_JOIN_MONTH`); `WARD_ADMIN` invitations ignore it. Redemption consumes the invitation, creates the ward membership and, for `WARD_MEMBER`, links the invited nurse record (or creates one with the user's display name) in one transaction. The role always comes from the invitation; extra fields such as `role` are rejected. A user who already belongs to the ward gets `409 ALREADY_WARD_MEMBER` and the invitation stays usable. Role changes for existing members are made by an admin as described under Member administration.

WARD_ADMIN invitation redemption returns 409 `ADMIN_NURSE_RECORD_CONFLICT` if the user has an active nurse record in that ward. This keeps managers out of the roster population without silently deactivating existing schedule data.

### Member administration

A `WARD_ADMIN` lists members with `GET /api/v1/wards/{wardId}/members` (roles, `active`, calendar access, linked `nurseId`) and changes one with `PATCH /api/v1/wards/{wardId}/memberships/{userId} {admin?, active?}` (at least one field; unchanged fields are no-ops). `admin:true` adds `WARD_ADMIN` next to any `WARD_MEMBER` role; `admin:false` removes it, except for an admin-only account (409 `MEMBER_ROLE_REQUIRED`: deactivate instead). `active:false` sets `ward_memberships.active=FALSE` (column added with `ALTER TABLE ... IF NOT EXISTS`, existing rows stay active): every access check ignores inactive rows, so the member gets `404 WARD_NOT_FOUND` at once on all ward endpoints and disappears from `/wards` and `/me`. The linked nurse record is deactivated too; reactivating the member restores access only, and the nurse record is reactivated explicitly in staff management. A ward always keeps at least one active admin: revoking or deactivating the last one returns 409 `LAST_WARD_ADMIN`. The change runs under the ward lock, so concurrent requests cannot leave zero admins; a request whose caller lost admin rights to an earlier one is refused with 403/404. Changes are audited as `MEMBER_ADMIN_GRANTED/REVOKED` and `MEMBER_DEACTIVATED/REACTIVATED`. Recovery from losing every admin account is not implemented.

| Role | Access |
| --- | --- |
| Signed in, no membership | `/me`, `/wards` (empty), invitation preview/redeem. Every ward endpoint returns `404 WARD_NOT_FOUND`. |
| `WARD_MEMBER` | Published ward calendar and own published calendar. Drafts, absences, generation, confirmation and invitations return `403 WARD_ADMIN_REQUIRED`. |
| `WARD_ADMIN` | Everything above plus drafts, absences, assignments, generation, confirmation and invitations. Not part of the rostered nurse pool. |

### Shift type settings

- `GET/POST /api/v1/wards/{wardId}/shift-types`, `PUT/DELETE .../shift-types/{shiftTypeId}` (package `policy`). Members read (`includeInactive`, response `{items, policyRevision}`); admins write. `DELETE` only deactivates (slots reference shift types); `PUT` with `active: true` reactivates.
- Times are `HH:mm`; `spansMidnight` must equal `endTime < startTime` (`SHIFT_SPANS_MIDNIGHT_MISMATCH`), equal times are `INVALID_SHIFT_INTERVAL`. `durationMinutes` adds 1440 when crossing midnight (22:00-07:00 = 540). `breakMinutes` 0..240; `recognizedMinutes` (default duration - break) must be 1..duration - break; `targetCreditMinutes` (default recognized) is what one shift credits toward the monthly target (the monthly target itself is A13). `code` is unique per ward including inactive types (409 `SHIFT_TYPE_CODE_DUPLICATE`); overlapping times are allowed.
- Every write takes the ward lock, bumps `ward_policy_revisions` and audits `SHIFT_TYPE_CREATED/UPDATED/DEACTIVATED`. Changing time, break, recognized/credit minutes, kind or active state of a type used by DRAFT rosters sets their `rosters.needs_revalidation` (exposed as `needsRevalidation`; cleared when the roster is published) and bumps their revision, so confirming or editing from the view before the change returns 412 `STALE_ROSTER_REVISION` until the roster is reloaded. A change of start, end or `spansMidnight` also rewrites those drafts' slot times, and is rejected with 422 `INVALID_SHIFT_INTERVAL` as a whole if any draft slot would end before it starts in the ward's time zone (a daylight-saving day). Display name or code alone changes neither. Slots store the code and kind they were created with, like their times: DRAFT slots follow type edits, PUBLISHED slots keep the published values, and the next month's rest rules are checked against those.

### Teams and career profiles

- Teams: `GET/POST /api/v1/wards/{wardId}/teams`, `PUT/DELETE .../teams/{teamId}`. Members read; admins change. A team with active nurses cannot be deleted.
- Team order (A5): `PUT /api/v1/wards/{wardId}/teams/{teamId}/order {nurseIds}` saves the admin's drag order of the team's active nurses, most senior first; it is the relative seniority inside the team and wins over hire or career dates. A list that does not match the current active members returns 409 `TEAM_ORDER_STALE`. Members not yet placed (new or moved) are listed after the ordered ones by career date.
- Roster settings (A5): `PUT /api/v1/wards/{wardId}/staff/{nurseId}/roster-settings {seniorEligible, stageOverride?}` sets senior eligibility (admin only, no automatic suggestion) and an optional stage override; `rosterStage` = override, else the confirmed computed stage. `GET .../staff/{nurseId}/adjustments` lists who changed order, team, eligibility, stage or career profile, newest first.
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
