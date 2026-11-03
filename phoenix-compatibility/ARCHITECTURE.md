# Phoenix compatibility architecture

## Target

Minecraft Java **1.21.1–1.21.11 inclusive**, selected through either a Vanilla or Fabric launcher profile. No manual Phoenix mod copying is required.

## Fabric profiles

CI resolves the published Yarn mappings and Fabric API build separately for every Minecraft release and compiles a matching Phoenix JAR. Each JAR is embedded in the APK. The launcher installs only the adapter whose version exactly matches the selected Fabric profile.

## Vanilla selections

An unmodified Vanilla client cannot load Fabric entrypoints or mixins. Phoenix therefore converts a selected Vanilla version into an isolated, launcher-managed Fabric profile at launch time. The selected profile is cloned in memory; the saved Vanilla profile remains unchanged. The game runs with Fabric internally, but is intended to remain compatible with ordinary Vanilla servers. This is not a pure unmodified Vanilla runtime.

## Verification

A successful compile is not a runtime pass. Every version/loader combination must be tested for startup, Phoenix loading screen, title menu, world entry, HUD, options, pause menu, module interaction and clean exit. The compatibility matrix reports build and runtime evidence separately.
