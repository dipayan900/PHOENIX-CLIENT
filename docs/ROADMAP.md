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
