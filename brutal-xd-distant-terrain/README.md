# Brutal_XD Distant Terrain

**Author credit:** Brutal_XD aka DIPAYAN  
**Target:** Minecraft 1.21.11 · Fabric · Java 21  
**Initial device profile:** vivo T4x / Mali-G615 MC2 · Balanced · 512-chunk target

## Current alpha implementation

- Fabric client mod entrypoint for Minecraft 1.21.11.
- Captures a compact 4-block-grid surface sample when a chunk is naturally loaded by the client.
- Stores samples locally under `gameDirectory/brutal_xd_distant_terrain/cache`, separated by server identity and dimension.
- Shows a small cache-status HUD line.
- Does not force-load chunks or make background requests to a server.
- Uses Java and Fabric APIs only; no bundled glibc, JNI, LMDB, Zstd, or desktop-native library.

## Important status

This is the **first buildable data/cache milestone**, not yet a complete Voxy-equivalent LOD renderer. The distant mesh generation, Android GLES rendering backend, seamless LOD transitions, cache eviction, and in-game settings screen are still to be implemented and tested. The HUD's 512 value is the configured target, not a claim that 512 chunks are currently being rendered.

The initial cache only records terrain the normal game client has already received. It does not magically discover unexplored terrain beyond server/client view distance.

## Build

Run `gradle build` with JDK 21 and Gradle 9.2.1 or use the GitHub Actions workflow. The remapped mod JAR is produced in `build/libs`.

## Compatibility approach

The project is an original implementation and does not copy Voxy source code. It intentionally avoids desktop Linux native dependencies. OpenGL ES compatibility and performance must be verified on the actual Android renderer/device before claiming support.
