# Brutal_XD Distant Terrain

**Author:** Brutal_XD aka DIPAYAN  
**Target:** Minecraft 1.21.11 · Fabric · Java 21  
**Design target:** Android-first, balanced rendering, configurable 64–512 chunk radius

## Current development build

- Client-only Fabric mod; it does not require a server-side plugin.
- Captures terrain only from chunks naturally delivered to the client.
- Stores one surface sample per horizontal block coordinate in a world/dimension-separated local cache.
- Builds camera-relative terrain meshes with distance-based LOD levels.
- Uses the Minecraft block atlas, with directional block-model sprites for the terrain skirts.
- Subdivides LOD cell boundaries using shared cached edge coordinates and adds skirts for missing edge data.
- Keeps file writes off the render thread and caps per-frame terrain tiles.
- Provides an F11 settings screen and a configurable 64–512 chunk target.
- Uses Java/Fabric APIs only; it does not bundle desktop glibc/JNI libraries.

## Scope and limitations

This is an original mobile-oriented distant-surface renderer, not a Voxy source-code clone. The current cache stores surface samples, not full 3D voxel sections. It therefore does not yet reproduce caves, overhangs, vertical structures, or full volumetric block geometry. Biome tinting and foliage tint indices are not yet implemented. Directional skirt textures and the finer surface grid are implemented in this development build, but their appearance and GLES compatibility still require in-game validation.

The configured radius is a target, not a promise that the client can obtain or render unexplored chunks. The mod does not bypass server view distance, force-load chunks, or request terrain the normal client has not received.

## Build and install

Use JDK 21 and Gradle 9.2.1:

```sh
gradle build --no-daemon
```

Install the remapped mod JAR from `build/libs` into the Fabric `mods` folder alongside Fabric API. GitHub Actions builds the project and publishes a downloadable workflow artifact for each source update.

## Compatibility

The project avoids desktop-native dependencies by design. Actual rendering, memory use, frame pacing, and 512-chunk behavior must be checked on the target Android launcher/GLES driver before calling a build stable.
