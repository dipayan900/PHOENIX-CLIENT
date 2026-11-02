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

## Cloudflare deployment (GitHub Actions)

The repository includes **Deploy Phoenix Social API** at `.github/workflows/deploy-phoenix-social.yml`. It deploys the Worker over HTTPS when manually triggered.

### One-time account setup

1. Have the Cloudflare account owner create a D1 database named `phoenix-social` in the same account that will host the Worker.
2. Copy the D1 database ID and the Cloudflare account ID from the dashboard.
3. Create an account API token restricted to this account with **D1 Edit** and **Workers Scripts Edit**. The first deployment creates the Worker, so the token may need **Workers Admin** for the initial creation; after the Worker exists, reduce it to the minimum permissions needed.
4. In the GitHub repository, open **Settings → Secrets and variables → Actions → New repository secret** and add:
   - `CF_API_TOKEN` — the Cloudflare API token.
   - `CF_ACCOUNT_ID` — the Cloudflare account ID.
   - `CF_D1_DATABASE_ID` — the D1 database ID for `phoenix-social`.
   - `PHOENIX_SOCIAL_SESSION_SECRET` — a long, randomly generated secret (at least 32 random bytes).
5. Open **Actions → Deploy Phoenix Social API → Run workflow**. The workflow applies `schema.sql`, sets the Worker session secret, and deploys the Worker.
6. Copy the exact `https://phoenix-social.<workers-subdomain>.workers.dev` URL shown in the deployment log. Check that adding `/health` returns JSON with `"ok":true`.
7. Enter that base URL in Phoenix Client → Friends. Do not add `/health` or an API route to the saved base URL.

If the Cloudflare account owner is under the provider's minimum age, an eligible parent/guardian or adult account owner must handle the account and credentials. Never paste secrets into chat, commit them to Git, or put them in the Android APK.

### Microsoft account sign-in

The current launcher screen supports guest account creation. Verified Minecraft Java account sign-in still needs its separate official Microsoft native-app OAuth flow and client ID integration; deploying this Worker alone does not enable that UI. The Worker verifies Java entitlement server-side when that endpoint is called.

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
