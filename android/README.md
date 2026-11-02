# NurseFlow Android prototype

Kotlin + Jetpack Compose app based on the [NurseFlow Figma file](https://www.figma.com/design/QtqAXph795SVIVHujdZvHF/NurseFlow?node-id=7-221).

**Connected to the server:** Kakao login, session restore/refresh/logout, ward list (`GET /me`), joining a ward with an invitation (preview → optional career start month and rank → join), the nurse's own career profile, and admin invitation create/share/revoke.

**Still demo data:** the roster screens opened after choosing a ward use fictional `DemoRoster` data in the app process. Their edits and the “publish” action are **not shared with the Spring Boot server or other users** until the roster API step.

## Setup

Add to `android/local.properties` (git-ignored, never commit keys):

```
kakao.nativeAppKey=<Kakao native app key>
nurseflow.apiBaseUrl=http://10.0.2.2:8080/
```

- `10.0.2.2` is the host computer from the Android emulator. On a physical device use the computer's LAN address, e.g. `http://192.168.0.10:8080/`, on the same Wi-Fi.
- Cleartext HTTP is allowed only in debug builds (`src/debug/res/xml/network_security_config.xml`). Release builds are HTTPS-only.
- Kakao Developers: register the Android platform with package `com.nurseflow.app` and this machine's debug key hash, enable Kakao Login and **OpenID Connect**, and add the native app key to the server's `NURSEFLOW_KAKAO_AUDIENCES`.
- Start the backend first (see [backend README](../backend/README.md)). Without any ward, a signed-in user sees the invitation-required screen; create the first ward and admin with `POST /api/v1/wards` and `NURSEFLOW_BOOTSTRAP_CODE`.

## Structure

| Package | Role |
| --- | --- |
| `core/network` | Retrofit API (mirrors `docs/openapi.yaml`), server error `code` → `ApiException`, bearer token and one-shot refresh on 401 |
| `core/session` | `SessionManager` (access token in memory, refresh serialized with a mutex), `SecureTokenStore` (refresh token AES-GCM encrypted with an Android Keystore key in DataStore) |
| `feature/auth` | Kakao SDK login with an OIDC nonce, login screen |
| `feature/ward`, `feature/invite`, `feature/profile` | Ward list, join/invite screens, career profile |
| `ui/NurseFlowApp.kt` | Existing roster prototype screens (demo data) |

Dependencies are wired by hand in `AppContainer`; it can move to Hilt without changing screens or repositories.

The prototype covers ward selection, administrator week/month roster views, candidate availability reasons, pre-collected leave/half-day entry, generation/review/confirmation screens, and personal/whole-ward published-calendar views. The Figma SVG icons are preserved under `app/src/main/assets/figma/`; PNG copies in `res/drawable-nodpi/` are used by Compose.

The fictional published calendar has a separate snapshot of assignments and absences for November 2026. Other months show an empty published state. New administrator-entered absences stay in the local draft until the demo publish action, so the read-only nurse view does not display draft changes.

From this directory, run `ANDROID_HOME=<Android SDK path> ./gradlew :app:assembleDebug`. Android SDK 35 and JDK 17 or newer are required by this project configuration. Before a release build, replace `DemoRoster` with the authenticated API repository described in [the initial MVP scope](../docs/mvp-scope.md), including Kakao invitation/membership checks and server-authoritative roster validation.
