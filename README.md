# Phoenix Client

Phoenix Client is an Android launcher for Minecraft: Java Edition, based on the Amethyst Android engine.

## Requested compatibility

The target is Minecraft Java **1.21.1–1.21.11 inclusive**. CI builds one Phoenix Fabric adapter per release and embeds all adapters in the APK. A Vanilla selection is automatically converted into a separate, isolated Fabric-backed Phoenix runtime; the saved Vanilla profile is not overwritten. This is not an unmodified Vanilla runtime, but is intended to remain compatible with ordinary Vanilla servers. No manual Phoenix mod copying is required.

See [the compatibility matrix](phoenix-compatibility/compatibility-matrix.json) and [the architecture](phoenix-compatibility/ARCHITECTURE.md).

## Build

Push to `integration/amethyst-phoenix` or run **Build Phoenix Client (Integrated Java Engine)**. The workflow builds the 11 adapters, embeds them in the APK and uploads one complete test package.

## Notices

Phoenix Client is not affiliated with Mojang or Microsoft. Minecraft: Java Edition requires a valid account entitlement. The launcher does not include proprietary Minecraft assets. The integrated engine is based on [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android) and retains its upstream licensing and credits.
