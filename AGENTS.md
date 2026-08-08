# AGENTS.md

Single-module Android app (`:app`), Kotlin + Jetpack Compose + Material3. Fresh Android Studio scaffold ("4K WA"), package `com.example.a4kwa`.

## Build & test (Windows)
- Build: `.\gradlew.bat assembleDebug`
- Unit tests (JVM, in `app/src/test`): `.\gradlew.bat test`
- Instrumented tests (in `app/src/androidTest`, need a connected device/emulator): `.\gradlew.bat connectedDebugAndroidTest`
- Lint: `.\gradlew.bat lint`
- Gradle wrapper is 9.2.1; daemon/toolchain JVM is 21 (`gradle/gradle-daemon-jvm.properties`), source/target compat Java 11.

## Toolchain quirks
- AGP 9.0.1 has built-in Kotlin support: `app/build.gradle.kts` applies ONLY `com.android.application` + `org.jetbrains.kotlin.plugin.compose`. Do NOT add `org.jetbrains.kotlin.android` — it breaks the build.
- `compileSdk` uses the AGP 9 DSL: `compileSdk { version = release(36) { minorApiLevel = 1 } }`. Don't "simplify" it to a plain int.
- minSdk 24, targetSdk 36. **No core-library desugaring configured** — avoid `java.time` and other API 26+ APIs without a `Build.VERSION` guard.

## Conventions
- Theme composable is `_4KWATheme` in `com.example.a4kwa.ui.theme` (leading underscore because `rootProject.name = "4K WA"` starts with a digit).
- UI code lives under `app/src/main/java/com/example/a4kwa`; package must stay `com.example.a4kwa` (namespace and applicationId).
- Activity uses `enableEdgeToEdge()` + Material3 `Scaffold`.

## Gotchas
- **This directory has no `.git`.** `git` commands resolve to the unrelated repo at `C:\Users\amirn` (home). Do not trust `git status`/`git log`/`git diff` output here, and don't stage/commit project files via git.
- `local.properties` holds the machine-specific SDK path (`C:\Users\amirn\AppData\Local\Android\Sdk`) and is git-ignored — never commit or edit it.
- Device/emulator testing: Android MCP tools are available (see `android-mcp.log` at repo root).
