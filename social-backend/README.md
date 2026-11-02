# Phoenix Social Backend

Cloudflare Workers + D1 service for Phoenix friend requests and private launcher chat.

## Important status

This is backend source code, not a live hosted service yet. It becomes reachable only after a Cloudflare account is configured, a D1 database is created, the migration is applied, and the Worker is deployed. The Android launcher must also be connected to the deployed API.

## Account rules

- **Premium account:** sign in with a Microsoft account that owns Minecraft: Java Edition. The Worker exchanges the Microsoft access token through Xbox Live and Minecraft Services, then checks Java Edition entitlements and obtains the official Minecraft profile UUID/name. A username alone never proves ownership.
- **Guest account:** supports Phoenix users who do not sign in with a Microsoft account. It is marked as a guest/unverified identity and cannot claim a Minecraft UUID.
- Both account types can send friend requests.
- Requests can only be sent to verified premium accounts.
- Once a premium recipient accepts, the two Phoenix accounts can exchange launcher-only text messages.
- This does not grant Minecraft multiplayer access or change Minecraft server authentication.

## Safety and abuse controls

- Messages are plain text only, max 500 characters.
- Requests, messages and guest registrations are rate-limited.
- Users can block and report accounts.
- Search only returns verified premium profiles.
- Friend requests are private; no public room or global broadcast chat exists.
- Do not put Microsoft client secrets, Cloudflare API tokens, or the Worker session secret in the Android APK or public repository.

## Cloudflare deployment

1. Install Node.js and Wrangler on a computer, or use Cloudflare's web dashboard where possible.
2. Create a Cloudflare D1 database named `phoenix-social`.
3. Copy its database ID into `wrangler.toml`.
4. Apply `schema.sql` to the D1 database.
5. Set a long random `SESSION_SECRET` Worker secret.
6. Deploy the Worker with Wrangler.
7. Register a Microsoft identity application as a public/native client and configure the Android redirect URI and Xbox Live sign-in permission. The resulting public client ID is an app identifier, not a secret. The Android app will use the official sign-in flow and pass the resulting Microsoft access token to this Worker.
8. Set the public API base URL in the Android client after deployment.

Never share Cloudflare API tokens or Microsoft credentials in chat or commit them to GitHub.

## API outline

- `POST /v1/auth/minecraft` — exchange a Microsoft access token, verify Minecraft Java ownership, and create a Phoenix session.
- `POST /v1/auth/guest` — create or restore a device-bound guest identity.
- `GET /v1/me` — current Phoenix identity.
- `GET /v1/users/search?q=...` — search verified premium recipients.
- `POST /v1/friends/requests` — send a request to a premium account.
- `GET /v1/friends/requests` — list incoming and outgoing requests.
- `POST /v1/friends/requests/:id/respond` — accept or decline an incoming request.
- `GET /v1/friends` — list accepted friends.
- `GET /v1/messages?with=USER_ID` — read a private conversation.
- `POST /v1/messages` — send a message to an accepted friend.
- `POST /v1/blocks` and `DELETE /v1/blocks/:userId` — block/unblock.
- `POST /v1/reports` — submit an abuse report.

All routes except authentication and health require a Phoenix bearer session.
