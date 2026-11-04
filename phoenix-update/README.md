# Phoenix Update System

## Implemented foundation
- Stable-channel JSON manifest endpoint and strict schema/HTTPS/URL/SHA-256 field validation.
- Update Center screen in the integrated engine dashboard.
- SHA-256 streaming helper for staged-file verification.
- Private, atomic Phoenix settings backup.
- Existing game profiles and worlds are not modified by the update-check screen.
- Explicit status reporting distinguishes build compatibility from runtime verification.

## Required before public automatic updates
1. Configure one persistent Android release signing key in protected CI secrets. Never commit the keystore or passwords.
2. Build and publish signed release APKs with monotonically increasing versionCode and the same applicationId/signing identity.
3. Add a signed manifest (Ed25519 or equivalent) and pin its public verification key in the client. SHA-256 alone detects corruption but does not authenticate who published a manifest.
4. Implement staged component downloads, checksum/signature validation, atomic activation and rollback for each component.
5. Implement Android PackageInstaller handoff for APK updates; Android user confirmation is expected for sideloaded installs.
6. Add version-specific dependency resolution and compatibility checks against the Phoenix matrix.
7. Add backup/restore validation for profile metadata and explicit opt-in world backups.
8. Add scheduled update notifications only after the release channel and notification permission flow are configured.

## Safety policy
- Never delete or overwrite saves, controls, server lists or existing game versions during an update.
- Download into temporary storage and verify before activation.
- Keep the previous known-good component set until the new set passes startup smoke tests.
- Never claim runtime compatibility based only on a successful CI build.
- Do not publish a release if the APK signing identity is not the established persistent release identity.

The current bootstrap manifest intentionally contains no APK URL or checksum. This prevents an incomplete updater from offering a non-installable or untrusted package.
