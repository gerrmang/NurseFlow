# NurseFlow Android prototype

Kotlin + Jetpack Compose screen prototype based on the [NurseFlow Figma file](https://www.figma.com/design/QtqAXph795SVIVHujdZvHF/NurseFlow?node-id=7-221). It currently uses fictional `DemoRoster` data in the app process. Local edits and the “publish” action are **not shared with the Spring Boot server or other users**.

The prototype covers ward selection, administrator week/month roster views, candidate availability reasons, pre-collected leave/half-day entry, generation/review/confirmation screens, and personal/whole-ward published-calendar views. The Figma SVG icons are preserved under `app/src/main/assets/figma/`; PNG copies in `res/drawable-nodpi/` are used by Compose.

From this directory, run `ANDROID_HOME=<Android SDK path> ./gradlew :app:assembleDebug`. Android SDK 35 and JDK 17 or newer are required by this project configuration. Before a release build, replace `DemoRoster` with the authenticated API repository described in [the initial MVP scope](../docs/mvp-scope.md), including Kakao invitation/membership checks and server-authoritative roster validation.
