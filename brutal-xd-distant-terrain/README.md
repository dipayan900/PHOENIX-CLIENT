# Brutal_XD Distant Terrain

**Author:** Brutal_XD aka DIPAYAN  
**Target:** Minecraft 1.21.11 · Fabric · Java 21  
**Design target:** Android-first rendering for ARM64 / OpenGL ES devices, with a configurable 64–512 chunk explored-terrain radius.

## Implemented systems

- Client-only Fabric mod; no server plugin is required.
- Captures terrain only from chunks naturally delivered to the client. It does not force-load or request remote chunks.
- Stores compact surface samples in a world- and dimension-separated persistent cache for distant LOD.
- Stores full 3D block-state snapshots for nearby chunks, including air positions, caves and overhang data.
- Reconstructs nearby chunk meshes from Minecraft's baked block models and block texture atlas.
- Uses camera-relative rendering, distance-based LOD sampling, boundary subdivision and terrain skirts.
- Performs cache file reads/writes on worker threads and bounds resident voxel/GPU mesh counts for mobile devices.
- Provides an in-game terrain settings screen through Sodium's Video Settings page when Sodium's config API is present. The F11 key is a fallback shortcut for this mod's own terrain settings screen; it does not hide or manage other mods.
- Uses Java/Fabric APIs and does not bundle desktop-native glibc/JNI libraries.

## Rendering model and limits

This is an original mobile-oriented distant-terrain implementation, not a Voxy source-code clone.

The renderer has two ranges:
- **Near field:** full 3D voxel snapshots and textured block-model meshes, with an 8-chunk detail radius.
- **Far field:** sampled surface LOD from explored chunks. It draws the surface and skirts, not complete distant 3D voxel sections.

Because the far field is built from terrain already seen by the normal client, the 512-chunk setting is a cache/render target, not a guarantee of 512 chunks of newly generated or server-hidden terrain. Caves, underground interiors, tall structures and overhangs are not fully reconstructed at far distances. Biome tinting and foliage tint indices are not implemented yet. The target Android GPU and launcher still need real-device validation for visual quality, memory use and frame pacing.

## Build

Use JDK 21 and Gradle 9.2.1:

```sh
gradle build --no-daemon
```

Install the remapped mod JAR from `build/libs` into the Fabric `mods` folder alongside Fabric API. Sodium is optional; when Sodium 0.8.x is installed, its Video Settings page exposes the mod's configuration screen. Without Sodium, the mod's F11 shortcut remains available.

GitHub Actions builds the project and publishes the JAR as a downloadable workflow artifact.

## Compatibility and safety

- Minecraft 1.21.11, Fabric Loader 0.18.4+, Java 21.
- No server-side plugin or server modification.
- No server view-distance bypass, chunk-forcing, anti-cheat evasion or account/entitlement bypass.
- This source build is not considered stable until the crash fix and rendering changes are confirmed in-game on the target Android device.
