# PHOENIX CLIENT 🔥

**Rise from the ashes. Rule the game.**

Phoenix Client is an Android-first Minecraft Java launcher project with an original fire-themed identity. The project is being built as a separate app and repository; it does not modify the user's other Android projects.

## Current implementation

- [x] Native Android app using Kotlin and Jetpack Compose
- [x] Phoenix dark/fire dashboard and bottom navigation
- [x] Local nickname editor with input filtering and automatic local persistence
- [x] Target Minecraft version selector with automatic local persistence
- [x] Three selectable launcher appearance themes: Inferno, Frost and Amethyst
- [x] Optional hand-off button for an already-installed Amethyst Android runtime (stable and debug package IDs)
- [x] GitHub Actions debug APK build workflow
- [ ] Minecraft Java runtime and actual game launch
- [ ] Microsoft account sign-in through the documented secure OAuth flow
- [ ] Official version manifest, game asset and library download/install pipeline
- [ ] Fabric/Forge/modpack integration
- [ ] In-game HUD, touch controls and rendered cosmetics

**Important:** This is still a launcher shell, not a playable Minecraft client. The Amethyst hand-off only opens the separate Amethyst app; it does not embed the runtime or pass Phoenix profile/version settings. The Play action intentionally explains that game launch is not available. The version picker currently saves a target version only; it does not download game files. Local nickname is not Microsoft authentication and does not grant online-mode access.

## Runtime direction

The former PojavLauncher Android repository was archived in September 2025. Its successor project is **Amethyst Android**, which documents Android Java runtimes, native graphics/input components, and Forge/Fabric support. Phoenix will evaluate Amethyst as the runtime foundation rather than copying an obsolete Pojav build or pretending a simple Activity launch is enough.

- Amethyst Android source: https://github.com/AngelAuraMC/Amethyst-Android
- Amethyst build instructions and component credits are documented upstream.
- Any integration must preserve applicable LGPLv3 and third-party notices/source obligations, and must be tested on real Android hardware.
- No Minecraft game assets or paid-account entitlements are bundled or bypassed.

## Build

Open the repository's **Actions** tab and run **Build Phoenix Client APK**, or push to the default branch. The workflow uploads a debug APK artifact named `phoenix-client-debug-apk`.

Build environment: JDK 17, Android SDK 35, Gradle 8.9.

## Project layout

- `app/`: Android application.
- `app/src/main/java/com/phoenix/client/`: Compose UI and app entry point.
- `.github/workflows/`: cloud APK build.
- `docs/ROADMAP.md`: implementation phases and integration gates.

## Legal and compatibility

Phoenix Client is independent and is not affiliated with Mojang or Microsoft. Do not redistribute Minecraft game files or third-party assets without permission. Review upstream licenses before integration. The project will not implement authentication/entitlement bypass, anti-cheat evasion, or unfair gameplay automation.
