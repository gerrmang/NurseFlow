# NurseFlow
간호사 통합 근무표 생성, 관리 앱

## Repository layout

| Directory | Project | Open with |
| --- | --- | --- |
| [`backend/`](backend/) | Kotlin + Spring Boot API and local H2 demo database | IntelliJ IDEA: **Open** the `backend` directory as a Gradle project |
| [`android/`](android/) | Kotlin + Jetpack Compose Android prototype | Android Studio: **Open** or **Import Project** from the `android` directory |
| [`docs/`](docs/) | MVP scope, API draft, project and review plans | Any editor |
| `web/` | Responsive web client | Planned; not created yet |

The Android and web clients share one backend API. The Android prototype currently uses fictional in-process data; its edits are not yet saved to the server. The API requires OIDC configuration and provisioned ward data before end-to-end use. See [MVP scope](docs/mvp-scope.md) and [Claude development handoff](docs/claude-development-handoff.md).

Choose the branch that contains the work you need when cloning. New feature work starts from the latest reviewed `dev`; the `main` branch is updated through a release PR. See [Git policy](docs/runbooks/git-flow.md) and [Claude next stage](docs/claude-next-stage.md).

## Local development

1. Open `backend/` in IntelliJ IDEA with JDK 21. Run `./gradlew test` in that directory. Configure `NURSEFLOW_OIDC_ISSUER_URI` and `NURSEFLOW_OIDC_AUDIENCE` before starting `bootRun`.
2. Open `android/` separately in Android Studio with Android SDK 35. Sync Gradle and run the `app` configuration on an emulator. See [Android prototype notes](android/README.md).
3. When API integration is added, the emulator reaches a backend running on the development computer through `10.0.2.2:8080`; physical devices use the computer's reachable network address. Keep local HTTP restricted to debug builds and use HTTPS for deployed environments.

Create feature branches from `dev`, submit feature PRs to `dev`, and release `dev` to `main` through a reviewed PR. See [Git policy](docs/runbooks/git-flow.md).
