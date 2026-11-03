# Phoenix Client

Phoenix Client is an Android launcher for Minecraft: Java Edition. The integration branch builds a Phoenix-branded app around the Amethyst Android Java launcher engine.

## Requested Minecraft compatibility

The target is **Minecraft Java 1.21.1–1.21.11 inclusive**, for both **Vanilla and Fabric**, with Phoenix integration managed by the launcher and no manual Phoenix mod installation. This is the target range, not a claim that every combination is currently implemented: the compatibility matrix records exact build and runtime evidence.

See [the compatibility matrix](phoenix-compatibility/compatibility-matrix.json) and [the integration architecture](phoenix-compatibility/ARCHITECTURE.md).

## Engine integration

The GitHub Actions workflow checks out the upstream Amethyst `v3_openjdk` source with its required submodules, downloads its OpenJDK 8 runtime bundle, applies Phoenix branding (application ID, launcher name, fire-themed colors and Phoenix launcher icon), and builds the Android debug APK.

The engine provides Minecraft Java version installation and launching, Microsoft account sign-in, Java runtimes, graphics/audio backends, Forge/Fabric support and mobile controls. These features are provided by the integrated engine; they are not separately reimplemented in the Phoenix Compose prototype.

## Build

Push to `integration/amethyst-phoenix` or run the **Build Phoenix Client (Integrated Java Engine)** workflow manually. Download the complete test package from the successful Actions run.

## Branding and notices

Phoenix is the user-facing product name and icon. The About / open-source notices retain required upstream and third-party copyright and license attributions. Phoenix Client is not affiliated with Mojang or Microsoft. Minecraft: Java Edition must be obtained with a valid account entitlement; the launcher does not include proprietary game assets.

## Source and licensing

The integrated engine is based on [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android), distributed under LGPLv3, with dependencies under their respective licenses. See the upstream license and credits in the engine source. Phoenix-specific files remain in this repository.

## Phoenix Cosmetics — launcher foundation

- [x] Cosmetics navigation destination.
- [x] Catalogue entries for capes, wings, accessories, emotes and effects.
- [x] Minecraft-native, hybrid and fantasy-3D style filters.
- [x] Category filters, catalogue preview, local inventory and equip slots.
- [x] Local development coin wallet with planned 100 / 300 / 750 / 1,500 / 3,500 / 7,500 coin tiers.
- [x] Creator-owner collection area reserved for future server-verified entitlement.

This is a launcher-side catalogue foundation, not yet a complete in-game renderer. Actual player rendering, animation playback, multiplayer synchronization, secure owner entitlements, server balances and payment processing remain separate milestones. The local wallet is test data only.
