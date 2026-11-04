# Phoenix Update System — implementation and release checklist

## Implemented in source
- Stable JSON manifest endpoint and bounded HTTPS manifest retrieval.
- Semantic-version, URL and SHA-256 field validation.
- Ed25519 detached-signature verification path. It is fail-closed until a public key is pinned.
- Bounded HTTPS staging-download helper using temporary files and durable flush.
- Component activation helper preserving the previous directory, plus explicit rollback helper.
- Schema-versioned JSON migration runner that migrates copies and validates before returning.
- WorkManager periodic update-check worker (12-hour interval, network-connected constraint).
- Android notification channel and update-available notification.
- Settings backup from the Phoenix Update Center.
- Existing Minecraft worlds are not touched by these helpers.

## Required before enabling public releases
1. Generate a dedicated Ed25519 manifest-signing key pair offline.
2. Put only the public X.509 key (Base64) into `PhoenixUpdateSecurity.ED25519_PUBLIC_KEY_X509_BASE64` and ship that pinned public key in the client.
3. Store the private manifest key only as a protected GitHub Actions secret. Never commit it.
4. Publish the raw stable.json and its detached Base64 Ed25519 signature as stable.json.sig. Sign the exact bytes of stable.json.
5. Provision a separate persistent Android APK release keystore and passwords in protected GitHub Secrets.
6. Configure the Android release build to sign with that same keystore on every release, keep applicationId `com.phoenix.client`, and increment versionCode.
7. Add a runtime notification-permission explanation/request for Android 13+ before relying on alerts.
8. Connect the staged downloader to the signed manifest's component list, validate each downloaded SHA-256, check compatibility, and activate only while the game is stopped.
9. Add project-specific migration steps as settings schemas change; test backup restoration and rollback on device.
10. Run clean integrated Android and Fabric builds, then device tests covering update, interrupted download, bad signature, insufficient storage, migration failure, rollback, worlds and controls.

## Security and reliability constraints
- An empty/unprovisioned Ed25519 public key intentionally rejects all manifests. Do not weaken this check to make the update screen appear successful.
- The current bootstrap manifest has no APK or component URLs and is not a production release.
- Android Package Installer confirmation is expected for sideloaded APK updates.
- WorkManager is best-effort periodic scheduling; Android controls exact execution timing.
- Helpers are not a claim that the complete production transaction or crash-free runtime has been validated.
- Never overwrite Minecraft saves, control maps, server lists or previous game versions as part of a launcher update.
