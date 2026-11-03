# Phoenix compatibility architecture

## Product requirement

Phoenix targets Minecraft Java Edition releases **1.21.1 through 1.21.11 inclusive**, on both **Vanilla** and **Fabric** profiles. Phoenix setup is launcher-managed: users must not have to find, download, or copy a Phoenix mod JAR themselves.

This is the requested compatibility range, not a claim that every combination is already implemented.

## Non-negotiable integration rules

1. Never put a Fabric-only JAR into a Vanilla profile and describe it as working.
2. Keep Phoenix Core independent of Minecraft internals wherever possible: feature state, settings, module metadata, cosmetics state and shared UI models belong in the common layer.
3. Keep Minecraft-version-specific rendering, screens, HUD hooks, input hooks and resource APIs behind version adapters.
4. Keep loader-specific entrypoints separate. Fabric profiles use a Fabric adapter. Vanilla profiles need a Phoenix-managed standalone integration launched by Phoenix without asking the player to install a mod manually.
5. Install into a Phoenix-managed profile/runtime area and preserve the user's original profile and game files. Provide rollback and integrity checks.
6. A build is not a runtime pass. Every supported version/loader pair must pass startup, title screen, world entry, HUD, options, pause menu, module interaction and clean-exit smoke tests on the target Android engine.

## Current verified state

- Fabric 1.21.1: source compiles and the JAR is embedded in the Android launcher build.
- Fabric 1.21.1 runtime: not yet verified on-device.
- Fabric 1.21.2–1.21.11: not yet implemented and verified.
- Vanilla 1.21.1–1.21.11: not yet implemented.
- Full requested range: not yet achieved.

The CI compatibility validator deliberately reports this distinction so a successful APK build cannot be mistaken for complete in-game compatibility.

## Delivery order

1. Make the existing 1.21.1 Fabric path pass real-device runtime smoke tests.
2. Add the remaining Fabric releases one at a time, resolving each release's mappings, loader and API dependencies and adapting changed Minecraft APIs.
3. Implement the managed Vanilla integration as a separate launch path and test it independently.
4. Run the full version × loader test matrix; publish only combinations that pass runtime checks.

## Acceptance criteria

A combination is marked supported only when its launcher-managed installation succeeds without manual mod copying and its runtime smoke tests pass. The launcher must show a clear compatibility status for unsupported combinations rather than silently launching an ordinary Minecraft menu and implying Phoenix loaded.
