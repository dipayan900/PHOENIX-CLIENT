# Phoenix player identity mark

The Phoenix mark is intended to appear as `🐦‍🔥 Brutal_XD` in the player list and server chat.

## Included
- `fabric/`: optional Fabric client branding mod for **Minecraft Java 1.21.1**. It sends the cosmetic `phoenix:brand` plugin-message channel when joining a server and marks the local player in the client tab list/nameplate.
- `paper/`: optional Paper server plugin. When installed by a server administrator, it recognises the channel and adds the Phoenix mark to that player's tab name and chat messages.

## Important limitations
- The launcher APK alone cannot make other clients or servers display a custom player prefix. For other players to see the mark, the server must run the Paper plugin. The client mod must be installed in a Fabric 1.21.1 profile.
- This first client mod targets Fabric 1.21.1 only; other Minecraft versions/loaders need separate compatibility builds.
- Minecraft's default font may not support the full bird emoji sequence on every platform/font. A bundled custom bitmap-font resource pack is a follow-up for consistent icon rendering.
- The client channel is an identity decoration only and can be imitated by other clients. Never use it for admin roles, owner access, permissions, balances, or security decisions.
- Servers that do not install the plugin will not broadcast the mark to other players. The client mod still marks the local player's own name locally.

## Build
- Fabric mod: run `gradle build` from `fabric/`; output is under `fabric/build/libs/`.
- Paper plugin: run `mvn package` from `paper/`; output is under `paper/target/`.
- Install the Fabric JAR in a Fabric 1.21.1 profile. A server owner installs the Paper JAR in `plugins/` and restarts the server.
