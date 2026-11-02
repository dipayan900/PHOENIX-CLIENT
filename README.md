# PHOENIX CLIENT 🔥

**Rise from the ashes. Rule the game.**

Phoenix Client is an Android-first Minecraft Java launcher project with an original fire-themed identity. It is maintained as a separate app and repository.

## Current implementation (0.3.0)

- [x] Native Android app using Kotlin and Jetpack Compose
- [x] Phoenix dark/fire dashboard and bottom navigation
- [x] Multiple local player profiles
- [x] Create, edit, select and delete local profiles
- [x] Per-profile nickname and target Minecraft version
- [x] Input validation for local nicknames (letters, numbers and underscore; max 16 characters)
- [x] Automatic local persistence of profiles and active selection
- [x] Three launcher appearance themes: Inferno, Frost and Amethyst
- [x] Optional hand-off button for an already-installed Amethyst Android runtime
- [x] GitHub Actions debug APK build workflow
- [ ] Integrated Minecraft Java runtime and actual game launch
- [ ] Microsoft account sign-in through documented secure OAuth
- [ ] Official version metadata and entitled game asset/library installation
- [ ] Fabric/Forge/modpack integration
- [ ] In-game HUD, touch controls and supported cosmetics

**Important:** Phoenix is currently a launcher foundation, not yet a playable Minecraft client. The Play action only opens the separate Amethyst app if it is installed; it does not embed the runtime or transfer Phoenix profile/version settings. Version selection is a local preset and does not download game files. A local nickname is not Microsoft authentication and does not grant online-mode access.

## Runtime direction

The primary runtime candidate is **Amethyst Android**. Before integrating it, audit upstream source, submodules, native libraries, JRE artifacts, supported ABIs, Android SDK requirements and all license notices.

- Amethyst Android source: https://github.com/AngelAuraMC/Amethyst-Android
- Preserve applicable LGPLv3 and third-party license/source obligations if code is incorporated.
- Validate installation, runtime lifecycle, graphics, input, audio, storage and crash handling on physical ARM64 Android devices.
- Do not bundle Minecraft assets or bypass paid-account entitlements.

## Build

Open the repository's **Actions** tab and run **Build Phoenix Client APK**, or push to the default branch. The workflow uploads a debug APK artifact named `phoenix-client-debug-apk`.

Build environment: JDK 17, Android SDK 35, Gradle 8.9.

## Project layout

- `app/`: Android application.
- `app/src/main/java/com/phoenix/client/`: Compose UI and app entry point.
- `.github/workflows/`: cloud APK build.
- `docs/ROADMAP.md`: implementation phases and integration gates.

## Legal and compatibility

Phoenix Client is independent and is not affiliated with Mojang or Microsoft. Do not redistribute Minecraft game files or third-party assets without permission. The project will not implement authentication/entitlement bypass, anti-cheat evasion, or unfair gameplay automation.


## Phoenix Cosmetics (launcher foundation)

- [x] Cosmetics navigation destination.
- [x] Starter catalogue: capes, wings, accessories, emotes and effects.
- [x] Minecraft-native, hybrid and fantasy-3D style filters.
- [x] Category filters, catalogue preview, local inventory and equip slots.
- [x] Local development coin wallet and planned bundle tiers: 100 / 300 / 750 / 1,500 / 3,500 / 7,500.
- [x] Owner collection area reserved for a future server-verified creator entitlement.

This is a launcher-side catalogue foundation, not an in-game renderer. 3D player previews, animated rendering, multiplayer synchronization, signed owner access, server balances and payment processing remain unimplemented. The local wallet is test data only and cannot be purchased or redeemed.
