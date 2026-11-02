# Phoenix cosmetics runtime, multiplayer and cloud plan

## Current launcher implementation
- Expanded catalogue and category/style filters are part of the integrated dashboard.
- Local preview selection, local inventory/equip slots and a development-only coin wallet are available.
- The catalogue is a launcher UI; emoji entries are not 3D assets.

## Runtime bridge contract
`PhoenixCosmeticRenderer` defines the engine-facing operations for player preview,
loadout application, emote playback and remote-player loadouts. It is an interface
only. The upstream Amethyst renderer does not implement it yet, so the launcher
must not report real in-game rendering as available.

The renderer implementation must:
1. Resolve a model/texture/animation by a stable cosmetic ID.
2. Validate model bounds, skeleton compatibility and file size before loading.
3. Attach capes and wings to compatible player-model bones and animate their
   secondary motion without modifying hitboxes or gameplay.
4. Play emotes only through supported player animation hooks, with cancellation
   and server policy checks.
5. Release GPU resources when the player, world or renderer is destroyed.

## Ready-made model imports
The asset ledger is `assets/cosmetics/LICENSES.md`. No third-party model is
currently bundled. Each model needs an exact creator source, licence, attribution,
redistribution permission and SHA-256 record before it can be shipped.

## Server authority and owner access
The client must never decide that an account is the owner from a username,
package flag, local preference or editable file. A trusted backend must verify
the authenticated account and return a short-lived signed entitlement. The
server must re-check the entitlement for privileged inventory operations.
Never put a server secret in the APK.

## Cloud inventory and coins
`PhoenixCosmeticCloudApi` defines inventory fetch, loadout sync and owner
entitlement verification contracts. No backend URL, authentication provider,
database or server signing key is configured in this repository, so cloud sync
is not active. The local wallet is development test data and is not a real
currency balance.

## Multiplayer synchronization
The client needs a server/plugin protocol to publish a player's equipped
cosmetic IDs and receive other players' allowed loadouts. Both server and
receiving client must support the same cosmetic protocol. Unknown IDs must be
ignored safely; cosmetic packets must never grant gameplay advantages.

## Payment
The coin bundle screen remains a non-purchasable placeholder. No payment SDK,
checkout, billing endpoint or real-money transaction is enabled.
