# NurseFlow Android

Kotlin + Jetpack Compose app. It has two clearly separated parts:

1. **Server-backed hospital/ward selection (implemented).** It shows the signed-in user's own wards (`GET /api/v1/wards`). For a hospital admin, it also shows the hospital's wards (`GET /api/v1/hospitals`, `GET /api/v1/hospitals/{id}/wards`) and lets them create a ward (`POST /api/v1/hospitals/{id}/wards`). After a successful create, the lists are reloaded and the new ward is selected. A ward member only sees their own wards and gets no hospital permissions.
2. **Figma prototype with fictional `DemoRoster` data.** It covers the ward picker, admin roster views, candidate reasons, absence entry, generation/review/confirmation, and published calendars. You open it explicitly with "가상 시연 데이터로 둘러보기", and it shows a banner saying the data is not shared with the server. **Server wards and demo data are never mixed.** A selected server ward shows a "roster integration pending" screen instead of demo rosters.

The design follows the [NurseFlow Figma file](https://www.figma.com/design/QtqAXph795SVIVHujdZvHF/NurseFlow?node-id=7-221). Shared visual primitives live in `ui/Components.kt`. The Figma SVG icons are kept under `app/src/main/assets/figma/`, and PNG copies in `res/drawable-nodpi/` are used by Compose.

## Code structure

| Package | Responsibility |
| --- | --- |
| `core/network` | `HttpTransport` (`HttpURLConnection`), `ApiClient`: bearer header, JSON, mapping `application/problem+json` `code` and HTTP status to `ApiError` |
| `core/session` | `SessionSource`. **Release**: no session (shows "login required"). **Debug only** (`src/debug`): a developer can paste an access token from the dev OIDC issuer. It is held in memory only and the server validates it normally. |
| `feature/organization/data` | Request/response DTOs matching `docs/openapi.yaml`, `RemoteOrganizationRepository` |
| `feature/organization/domain` | `MyWard`, `Hospital`, `HospitalWard`, repository interface, ward-name rules |
| `feature/organization/ui` | `WardSelectionViewModel` (state, errors, idempotent create), `WardSelectionScreen` |

Handled states are loading, empty lists, field errors (`INVALID_WARD_NAME`, `WARD_NAME_TAKEN`, `INVALID_TIME_ZONE`), session expiry (401, then sign out), permission (403), hidden resources (404), and network/5xx with retry. For a create whose outcome is unknown (network failure, 5xx, or a 2xx response the app cannot parse), retrying the same input reuses the same `Idempotency-Key`. A create is one locked operation, from the request until the lists are refreshed and the new ward is selected. During it, switching hospitals, opening, closing or editing the form, choosing a ward, refreshing, and resubmitting are blocked in both the UI and the ViewModel. If the ward was created but the list refresh fails, the retry reloads only the lists and never resends the create request. Changing the input or receiving a definite response starts a new key.

Keyboard handling: `MainActivity` uses `windowSoftInputMode="adjustResize"`. With edge-to-edge (forced at targetSdk 35 on Android 15+), screens with inputs lower on the page apply `imePadding()`, so fields and buttons stay reachable above the keyboard. The debug token field is a password input, so the keyboard neither suggests nor learns the token.

**Not yet available:** Kakao login and server session exchange, invitation acceptance, server-backed roster screens. There is no hardcoded token or admin account and no auth bypass.

## Build and test

From this directory, with Android SDK 35 and JDK 17+:

```sh
ANDROID_HOME=<Android SDK path> ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

- The debug build calls `http://10.0.2.2:8080` (host machine from the emulator). Override it with `-Pnurseflow.apiBaseUrl=...`. Cleartext HTTP is allowed only in the debug build and only for `10.0.2.2`, `localhost`, and `127.0.0.1`.
- The release build has no API base URL until the real login/environment setup exists.

Local integration (app network layer ↔ real backend with real JWT validation, without Kakao or device UI): from the repository root, run `scripts/local-e2e/run-hospital-ward-e2e.sh`. It needs `node`, JDK 21, and `ANDROID_HOME`. It starts a 127.0.0.1-only throwaway OIDC issuer and the backend jar on a temporary H2 file DB with an operator-bootstrapped synthetic hospital. It then runs `LiveOrganizationApiTest`, which is skipped in normal unit-test runs.
