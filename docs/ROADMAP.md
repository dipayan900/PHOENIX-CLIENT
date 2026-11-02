# Phoenix Client technical roadmap

## Phase 1 — Android foundation (implemented)
- Native Compose launcher shell and Phoenix design system.
- GitHub Actions debug APK pipeline.
- Multiple local profiles with create, edit, select and delete actions.
- Per-profile nickname and target Minecraft version, persisted on device.
- Three persistent launcher themes.
- Clear messaging that local profile/version selections are not authentication or installed game content.
- Optional Amethyst app hand-off when the separate runtime is installed.

## Phase 2 — Runtime feasibility and integration (next major milestone)
- Use **AngelAuraMC/Amethyst-Android** as the primary runtime candidate. The former PojavLauncherTeam Android repository is archived; Amethyst identifies itself as its successor.
- Audit the upstream branch, recursive submodules, native libraries, prebuilt JRE artifacts, Gradle tasks, supported ABIs, Android SDK requirements and license notices.
- Decide whether Phoenix should be a branded fork or a separate front-end with a supported runtime hand-off. Do not copy upstream code until the legal and technical integration model is clear.
- Preserve applicable LGPLv3 and all third-party license/source obligations if code is incorporated.
- Validate installation, runtime lifecycle, game surface, input, audio, storage permissions and crash handling on physical ARM64 Android devices.
- Only enable actual game launch after a real launch path is integrated and tested.

## Phase 3 — Accounts and game content
- Implement Microsoft's documented OAuth device authorization flow; never collect account passwords.
- Store tokens using Android Keystore-backed encryption and follow the provider's current OAuth requirements.
- Parse official version metadata and download game libraries/assets only for users entitled to use them.
- Keep local offline nicknames separate from authenticated player identity.

## Phase 4 — Mods and client features
- Add compatible Fabric/Forge and modpack installation only after the runtime is working.
- Add HUD and touch-control configuration through supported runtime extension points.
- Keep launcher themes separate from in-game skins/capes; add rendered cosmetics only when there is a legitimate compatible integration.

## Phase 5 — Quality and release
- Device compatibility matrix, memory/performance testing, accessibility, crash recovery and release signing.
- Reproducible builds, transparent support matrix and required third-party notices.

## Non-goals
- Authentication or entitlement bypass.
- Anti-cheat evasion or unfair gameplay automation.
- Redistributing proprietary Minecraft files or unlicensed third-party assets.
