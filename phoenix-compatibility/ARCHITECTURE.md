# Phoenix compatibility architecture

## Product requirement

Phoenix targets Minecraft Java Edition releases from **1.8.9 through the latest stable release**, on both **Vanilla** and **Fabric** profiles. Phoenix setup is launcher-managed: users must not have to find, download, or copy a Phoenix mod JAR themselves.

This is a target, not a claim that every combination is already implemented.

## Non-negotiable integration rules

1. Never put a Fabric-only JAR into a Vanilla profile and describe it as working.
2. Keep Phoenix Core independent of Minecraft internals wherever possible: feature state, settings, module metadata, cosmetics state and shared UI models belong in the common layer.
3. Keep Minecraft-version-specific rendering, screens, HUD hooks, input hooks and resource APIs behind version adapters.
4. Keep loader-specific entrypoints separate. Fabric profiles use a Fabric adapter. Vanilla profiles need a Phoenix-managed standalone bootstrap/client integration that is launched by Phoenix without asking the player to install a mod manually.
5. Install into a Phoenix-managed profile/runtime area and preserve the user's original profile and game files. Provide rollback and integrity checks.
6. A build is not a runtime pass. Every supported version/loader pair must pass startup, title screen, world entry, HUD, options, pause menu, module interaction and clean-exit smoke tests on the target Android engine.

## Adapter families

| Family | Minecraft range | Why it is separate |
|---|---|---|
| Legacy | 1.8.9–1.12.2 | Older GUI, rendering, mappings and input APIs |
| Transition | 1.13–1.16.5 | Resource, rendering and client API transitions |
| Modern | 1.17–latest | Modern rendering and screen APIs, with version-specific mappings |

The family boundaries are engineering groupings, not a promise that one binary works for every version in that range. Individual version adapters may still be required.

## Current verified state

- Fabric 1.21.1: source compiles and the JAR is embedded in the Android launcher build.
- Fabric 1.21.1 runtime: not yet verified on-device.
- Vanilla: not yet implemented.
- Other Minecraft versions: not yet implemented.
- Full 1.8.9–latest compatibility: not yet achieved.

The CI compatibility validator deliberately reports this distinction so a successful APK build cannot be mistaken for complete in-game compatibility.

## Delivery order

1. Extract shared Phoenix Core interfaces from the current 1.21.1 client.
2. Make the existing 1.21.1 Fabric path pass real-device runtime smoke tests.
3. Implement the managed Vanilla bootstrap path and test it independently.
4. Add the modern adapter family one Minecraft release at a time.
5. Add the transition and legacy families, beginning with 1.16.5 and 1.8.9 respectively.
6. Expand the tested version matrix and publish only combinations that pass the runtime gate.

## Acceptance criteria

A combination is marked supported only when its launcher-managed installation succeeds without manual mod copying and its runtime smoke tests pass. The launcher must show a clear compatibility status for unsupported combinations rather than silently launching an ordinary Minecraft menu and implying Phoenix loaded.
