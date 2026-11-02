# Phoenix cosmetics runtime, multiplayer and cloud status

## Launcher
- Phoenix launcher contains the cosmetic catalogue, filters, local preview, local development wallet and wardrobe UI.
- The launcher wallet is demo data and is separate from the server inventory.

## In-game Fabric client (Minecraft Java 1.21.1)
- A procedural FeatureRenderer attaches original cuboid cape, wing, headwear and backpack meshes to player models.
- Cape and wing panels use Minecraft-installed wool textures at runtime; Phoenix does not bundle Mojang texture files or redistribute third-party models.
- A player-model mixin plays client-side emote poses including wave, salute, victory, clap, dance, bow, sit, meditate and more.
- Server-supplied loadout packets are length-limited and cosmetic IDs are allowlisted before entering client state.
- The add-on currently targets Fabric 1.21.1 only. Other versions/loaders need separate builds and testing.

## Paper server inventory and multiplayer sync
- Paper stores wallets, owned IDs and equipped slots in a persistent SQLite database under the plugin data folder.
- Commands: /phoenix help, /phoenix shop, /phoenix balance, /phoenix claim, /phoenix buy <id>, /phoenix equip <id>, /phoenix unequip <slot>, /phoenix wardrobe, /phoenix emote <id>, and /phoenix emote stop.
- The server sends equipped player loadouts to connected Phoenix Fabric clients, allowing compatible clients to render other players' cosmetics.
- This is a persistent database on the Minecraft server, not a deployed multi-server cloud service. Cross-server global inventory requires hosting and configuring a shared database/API.

## Owner verification
- Owner status is determined server-side by exact authenticated Minecraft UUID in plugins/PhoenixBranding/config.yml.
- The owner UUID must be configured by the server operator. A username, local launcher preference, client packet or emoji prefix never grants owner access.
- Never embed a server secret in the launcher APK.

## Assets and limitations
- Cape, wing, headwear and backpack meshes in the first renderer are procedural geometry authored in source code.
- No third-party model files are currently imported. The asset ledger is assets/cosmetics/LICENSES.md.
- This first pass is a simple geometric renderer, not a full custom Blockbench/GeckoLib-grade rig. Detailed licensed models and item-specific rigs remain future work.

## Payment
- Real-money checkout is intentionally disabled. Server coins are free demo/test points with a daily claim. No payment SDK, billing endpoint or real-money transaction is enabled.