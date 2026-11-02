# Phoenix Client technical roadmap

## Phase 1 — Android foundation
- Native Compose launcher shell and Phoenix design system.
- CI debug APK pipeline.
- Local launcher profile and version selection prototype.

## Phase 2 — Runtime feasibility and integration
- Audit current PojavLauncher source, supported Android architectures, runtime packaging, upstream license and contribution requirements.
- Decide whether to integrate through a supported external launch contract or maintain a compliant fork.
- Validate on a physical Android device before advertising game launch support.
- Implement game directory, runtime lifecycle, logs and crash reporting.

## Phase 3 — Accounts and content
- Secure Microsoft OAuth device authorization using Microsoft's documented flow.
- Store tokens using Android Keystore-backed encryption; never collect passwords.
- Implement official version manifest parsing and user-authorized game downloads.
- Keep local offline launcher nicknames separate from authenticated player identity.

## Phase 4 — Mods and client features
- Add compatible Fabric/Forge installation only after runtime support is established.
- Build settings and HUD configuration adapters where the game/runtime exposes supported extension points.
- Implement cosmetics as client-side visuals unless a server protocol explicitly supports them.

## Phase 5 — Quality
- Device compatibility matrix, memory/performance testing, accessibility, crash recovery and release signing.
- Publish reproducible builds and a transparent feature support matrix.

## Non-goals
- Authentication or entitlement bypass.
- Anti-cheat evasion or unfair gameplay automation.
- Redistributing proprietary Minecraft files or unlicensed client assets.
