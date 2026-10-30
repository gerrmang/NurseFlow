# NurseFlow Backend

Kotlin + Spring Boot API for drafting, manually editing, validating and publishing a ward roster. H2 stores local data in `backend/nurseflow.mv.db`.

## Configuration and provisioning

Java 21, an OIDC issuer and an API audience are required. Set `NURSEFLOW_OIDC_ISSUER_URI` to the issuer that signs access tokens and `NURSEFLOW_OIDC_AUDIENCE` to this API's audience. Spring Security validates both JWT claims; there is no anonymous development bypass. The service cannot serve scheduling requests until `wards`, `ward_memberships`, `nurses`, and `shift_types` have been provisioned in the local H2 database. Provisioning endpoints and production database migration are separate work.

`ward_memberships.subject` and `nurses.subject` must match the validated JWT `sub` exactly. Administrators need a `WARD_ADMIN` membership. The whole ward calendar also requires `can_view_ward_calendar=TRUE`. The personal calendar resolves its nurse ID from the authenticated subject and never accepts a nurse ID in the request.

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

Writes require the current strong `If-Match` ETag. Mutations that require `Idempotency-Key` retain their original response in H2, so a retry with the same key and payload returns the prior result. The generator is a heuristic and does not claim optimality. Kakao login and invitation/membership APIs, staff/shift configuration APIs, Android/web API integration, production database migrations, and full contract coverage remain to be built; see the [MVP scope](../docs/mvp-scope.md).

## Build

Open this directory as an existing Gradle project in IntelliJ IDEA and select JDK 21. Run `./gradlew test` from `backend/` for the local checks. To start the API, provide `NURSEFLOW_OIDC_ISSUER_URI` and `NURSEFLOW_OIDC_AUDIENCE` in the run configuration, then run `./gradlew bootRun`. The API has no anonymous development bypass, and ward data must be provisioned before scheduling requests can succeed.
