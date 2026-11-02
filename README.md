# Phoenix Client

Phoenix Client is an Android launcher for Minecraft: Java Edition. The integration branch builds a Phoenix-branded app around the Amethyst Android Java launcher engine.

## Engine integration

The GitHub Actions workflow checks out the upstream Amethyst `v3_openjdk` source with its required submodules, downloads its OpenJDK 8 runtime bundle, applies Phoenix branding (application ID, launcher name, fire-themed colors and Phoenix launcher icon), and builds the Android debug APK.

The engine provides Minecraft Java version installation and launching, Microsoft account sign-in, Java runtimes, graphics/audio backends, Forge/Fabric support and mobile controls. These features are provided by the integrated engine; they are not separately reimplemented in the Phoenix Compose prototype.

## Build

Push to `integration/amethyst-phoenix` or run the **Build Phoenix Client (Integrated Java Engine)** workflow manually. Download the `phoenix-client-debug-apk` artifact from the successful Actions run.

## Branding and notices

Phoenix is the user-facing product name and icon. The About / open-source notices retain required upstream and third-party copyright and license attributions. Phoenix Client is not affiliated with Mojang or Microsoft. Minecraft: Java Edition must be obtained with a valid account entitlement; the launcher does not include proprietary game assets.

## Source and licensing

The integrated engine is based on [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android), distributed under LGPLv3, with dependencies under their respective licenses. See the upstream license and credits in the engine source. Phoenix-specific files remain in this repository.
