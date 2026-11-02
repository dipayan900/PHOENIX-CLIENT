# Phoenix cosmetics and player branding

Phoenix cosmetics are split between the launcher catalogue, the Fabric in-game client, and an optional Paper server plugin.

## Included

- **Launcher:** cosmetic catalogue, categories/styles, local preview, wardrobe and development demo wallet.
- **Fabric 1.21.1 client:** procedural 3D cape, wing, headwear and backpack attachments; client-side animated emote poses; server-supplied multiplayer loadout receiver; local Phoenix tab-list mark.
- **Paper 1.21.1 server plugin:** persistent SQLite wallet, owned inventory and equipped slots; daily free demo coins; cosmetic shop/equip/wardrobe/emote commands; server-authoritative owner UUID check; loadout broadcast to connected Phoenix clients; Phoenix player-list/chat mark.
- **Original geometry:** first-pass meshes are generated in Java source. The mod references Minecraft's installed wool block textures at runtime and does not bundle Mojang texture files or third-party models.

## Server commands

- `/phoenix help`
- `/phoenix shop`
- `/phoenix balance`
- `/phoenix claim` — daily free demo coins
- `/phoenix buy <cosmetic-id>`
- `/phoenix equip <cosmetic-id>`
- `/phoenix unequip <cape|wings|head|back|emote>`
- `/phoenix wardrobe`
- `/phoenix emote <emote-id>`
- `/phoenix emote stop`
- `/phoenix owner`

The server database lives under `plugins/PhoenixBranding/cosmetics.db`. Back it up with the server stopped. Configure the exact authenticated owner Minecraft UUID in `plugins/PhoenixBranding/config.yml`; leaving it blank disables owner entitlements. A username, local preference, client packet or branding prefix never grants owner privileges.

## Installation

1. Install the Fabric client JAR in a Fabric **Minecraft Java 1.21.1** profile.
2. Install the Paper plugin JAR in the server's `plugins/` folder and restart the server.
3. Ensure players who should see other players' cosmetics use the compatible Phoenix Fabric client.
4. Use the server commands above to unlock and equip cosmetics.

The Paper plugin is server-side inventory persistence for that server. It is not a deployed global cloud service or cross-server shared account system; that requires an independently hosted shared database/API. The launcher demo wallet is separate.

## Limitations and safety

- The first renderer uses simple procedural cuboid meshes, not detailed custom Blockbench rigs. Item-specific mesh art, additional animation rigs and broad Minecraft-version compatibility remain future work.
- The first mod targets Fabric 1.21.1 only. Other versions/loaders require separate compatibility builds.
- The branding marker is cosmetic only and can be imitated; it is never an authorization signal.
- Real-money checkout is disabled. Coins are free demo/test points only.
- Do not import third-party models unless redistribution rights, attribution, licence and checksum are recorded in `assets/cosmetics/LICENSES.md`.

## Build

- Fabric: run `gradle build` from `fabric/`.
- Paper: run `mvn package` from `paper/`.
- GitHub Actions workflow: `.github/workflows/phoenix-branding.yml`.
