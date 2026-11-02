# Phoenix Client roadmap

## Integration branch: `integration/amethyst-phoenix`

- [x] Create an isolated engine-integration branch.
- [x] Add a reproducible CI workflow that checks out the Amethyst Android engine and recursive submodules.
- [x] Add Phoenix application ID, app name, icon, and warm dark/fire palette branding during the build.
- [x] Add OpenJDK runtime artifact retrieval to the CI build.
- [ ] Verify a complete APK build on GitHub Actions.
- [ ] Install and test the APK on the vivo T4x 5G / Android 16.
- [ ] Replace the engine's stock launcher landing experience with a Phoenix-native dashboard while retaining its game/runtime activities.
- [ ] Connect Phoenix saved profiles and per-profile versions to the engine's real launch configuration.
- [ ] Verify Microsoft sign-in, game entitlement checks, version installation, and launch.
- [ ] Verify Fabric, Forge, modpack import/management, and custom touch controls on-device.
- [ ] Tune graphics backend and performance for the target device.

## Notes

Engine features are supplied by the integrated Amethyst codebase, not by mock UI. Microsoft authentication must use Microsoft's supported authorization flow. Required copyright and license notices must remain accessible in the app. A CI build is not considered device verification.


## Cosmetics — launcher foundation
- [x] Cosmetics navigation and starter catalogue for capes, wings, accessories, emotes and effects.
- [x] Minecraft-native, hybrid and fantasy-3D style filters.
- [x] Preview selection, local inventory, equip slots and development coin wallet.
- [x] Planned coin tiers: 100, 300, 750, 1,500, 3,500 and 7,500.
- [ ] Import ready-made assets only with verified redistribution licenses and attribution records.
- [ ] Integrate a 3D player preview and runtime cosmetic renderer.
- [ ] Implement signed creator-owner entitlements and server-authoritative inventory.
- [ ] Implement compatible multiplayer cosmetic synchronization and server support.
- [ ] Keep real-money checkout disabled until a compliant adult-managed release setup exists.
