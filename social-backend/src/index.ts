export interface Env {
  DB: D1Database;
  SESSION_SECRET: string;
  APP_NAME?: string;
}

type User = {
  id: string;
  username: string;
  auth_type: "premium" | "guest";
  minecraft_uuid: string | null;
  minecraft_name: string | null;
};

type Session = { sub: string; iat: number; exp: number };

const encoder = new TextEncoder();
const json = (data: unknown, status = 200, extra: Record<string, string> = {}) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...extra },
  });

const fail = (message: string, status = 400) => json({ error: message }, status);
const now = () => Math.floor(Date.now() / 1000);
const id = () => crypto.randomUUID();

function b64url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
function decodeB64url(value: string): Uint8Array {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((value.length + 3) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, c => c.charCodeAt(0));
}
async function sha256(value: string): Promise<string> {
  return b64url(new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(value))));
}
async function hmacKey(secret: string) {
  return crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"]);
}
async function createSession(userId: string, secret: string): Promise<string> {
  const issued = now();
  const header = b64url(encoder.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const payload = b64url(encoder.encode(JSON.stringify({ sub: userId, iat: issued, exp: issued + 60 * 60 * 24 * 30 })));
  const input = header + "." + payload;
  const signature = new Uint8Array(await crypto.subtle.sign("HMAC", await hmacKey(secret), encoder.encode(input)));
  return input + "." + b64url(signature);
}
async function verifySession(token: string, secret: string): Promise<Session | null> {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const input = parts[0] + "." + parts[1];
    const valid = await crypto.subtle.verify("HMAC", await hmacKey(secret), decodeB64url(parts[2]), encoder.encode(input));
    if (!valid) return null;
    const session = JSON.parse(new TextDecoder().decode(decodeB64url(parts[1]))) as Session;
    if (!session.sub || session.exp <= now()) return null;
    return session;
  } catch {
    return null;
  }
}
async function body<T>(request: Request): Promise<T | null> {
  try { return await request.json() as T; } catch { return null; }
}
function cleanUsername(value: unknown): string {
  return typeof value === "string" ? value.trim().replace(/[^a-zA-Z0-9_]/g, "").slice(0, 16) : "";
}
async function rateLimit(env: Env, key: string, limit: number, windowSeconds: number): Promise<boolean> {
  const timestamp = now();
  const row = await env.DB.prepare(
    `INSERT INTO rate_limits(rate_key, window_start, request_count)
     VALUES (?, ?, 1)
     ON CONFLICT(rate_key) DO UPDATE SET
       request_count = CASE WHEN window_start <= ? THEN 1 ELSE request_count + 1 END,
       window_start = CASE WHEN window_start <= ? THEN ? ELSE window_start END
     RETURNING request_count`
  ).bind(key, timestamp, timestamp - windowSeconds, timestamp - windowSeconds, timestamp).first<{ request_count: number }>();
  return (row?.request_count ?? limit + 1) <= limit;
}
async function getUser(env: Env, userId: string): Promise<User | null> {
  return env.DB.prepare("SELECT id, username, auth_type, minecraft_uuid, minecraft_name FROM users WHERE id = ?")
    .bind(userId).first<User>();
}
async function requireUser(request: Request, env: Env): Promise<User | Response> {
  const authorization = request.headers.get("authorization") ?? "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  if (!token) return fail("Sign in to Phoenix Social first.", 401);
  const session = await verifySession(token, env.SESSION_SECRET);
  if (!session) return fail("Your Phoenix session is invalid or expired.", 401);
  const user = await getUser(env, session.sub);
  return user ?? fail("Phoenix account was not found.", 401);
}
function isResponse(value: User | Response): value is Response { return value instanceof Response; }

async function minecraftLogin(msAccessToken: string) {
  const xblResponse = await fetch("https://user.auth.xboxlive.com/user/authenticate", {
    method: "POST",
    headers: { "content-type": "application/json", "accept": "application/json" },
    body: JSON.stringify({
      Properties: { AuthMethod: "RPS", SiteName: "user.auth.xboxlive.com", RpsTicket: "d=" + msAccessToken },
      RelyingParty: "http://auth.xboxlive.com",
      TokenType: "JWT",
    }),
  });
  if (!xblResponse.ok) throw new Error("Microsoft/Xbox authentication was rejected.");
  const xbl = await xblResponse.json() as { Token: string; DisplayClaims?: { xui?: Array<{ uhs?: string }> } };
  const xstsResponse = await fetch("https://xsts.auth.xboxlive.com/xsts/authorize", {
    method: "POST",
    headers: { "content-type": "application/json", "accept": "application/json" },
    body: JSON.stringify({
      Properties: { SandboxId: "RETAIL", UserTokens: [xbl.Token] },
      RelyingParty: "rp://api.minecraftservices.com/",
      TokenType: "JWT",
    }),
  });
  if (!xstsResponse.ok) throw new Error("Xbox account could not be authorized for Minecraft.");
  const xsts = await xstsResponse.json() as { Token: string; DisplayClaims?: { xui?: Array<{ uhs?: string }> } };
  const uhs = xsts.DisplayClaims?.xui?.[0]?.uhs;
  if (!uhs) throw new Error("Xbox account identity was not returned.");
  const mcResponse = await fetch("https://api.minecraftservices.com/authentication/login_with_xbox", {
    method: "POST",
    headers: { "content-type": "application/json", "accept": "application/json" },
    body: JSON.stringify({ identityToken: "XBL3.0 x=" + uhs + ";" + xsts.Token }),
  });
  if (!mcResponse.ok) throw new Error("Minecraft account sign-in failed.");
  const mc = await mcResponse.json() as { access_token: string };
  const entitlementResponse = await fetch("https://api.minecraftservices.com/entitlements/mcstore", {
    headers: { authorization: "Bearer " + mc.access_token },
  });
  if (!entitlementResponse.ok) throw new Error("Minecraft entitlement check failed.");
  const entitlements = await entitlementResponse.json() as { items?: Array<{ name?: string }> };
  const ownsJava = (entitlements.items ?? []).some(item =>
    item.name === "game_minecraft" || item.name === "product_minecraft"
  );
  if (!ownsJava) throw new Error("This Microsoft account does not have a verified Minecraft Java entitlement.");
  const profileResponse = await fetch("https://api.minecraftservices.com/minecraft/profile", {
    headers: { authorization: "Bearer " + mc.access_token },
  });
  if (!profileResponse.ok) throw new Error("A Minecraft Java profile was not found.");
  const profile = await profileResponse.json() as { id: string; name: string };
  if (!profile.id || !profile.name) throw new Error("Minecraft profile data was incomplete.");
  return profile;
}

async function authMinecraft(request: Request, env: Env): Promise<Response> {
  if (!await rateLimit(env, "minecraft-auth:" + (request.headers.get("CF-Connecting-IP") ?? "unknown"), 10, 300))
    return fail("Too many sign-in attempts. Try again later.", 429);
  const data = await body<{ microsoftAccessToken?: string }>(request);
  const accessToken = data?.microsoftAccessToken;
  if (typeof accessToken !== "string" || accessToken.length < 100 || accessToken.length > 12000)
    return fail("A valid Microsoft access token is required.");
  try {
    const profile = await minecraftLogin(accessToken);
    const timestamp = now();
    let user = await env.DB.prepare("SELECT id, username, auth_type, minecraft_uuid, minecraft_name FROM users WHERE minecraft_uuid = ?")
      .bind(profile.id).first<User>();
    if (!user) {
      const base = cleanUsername(profile.name) || "MinecraftPlayer";
      let username = base;
      const conflict = await env.DB.prepare("SELECT id FROM users WHERE username = ?").bind(username).first();
      if (conflict) username = (base.slice(0, 11) + "_" + profile.id.slice(0, 4)).slice(0, 16);
      const userId = id();
      await env.DB.prepare(
        "INSERT INTO users(id, username, auth_type, minecraft_uuid, minecraft_name, created_at, last_seen) VALUES (?, ?, 'premium', ?, ?, ?, ?)"
      ).bind(userId, username, profile.id, profile.name, timestamp, timestamp).run();
      user = await getUser(env, userId);
    } else {
      await env.DB.prepare("UPDATE users SET last_seen = ?, minecraft_name = ? WHERE id = ?")
        .bind(timestamp, profile.name, user.id).run();
      user = await getUser(env, user.id);
    }
    if (!user) return fail("Could not create the Phoenix profile.", 500);
    const session = await createSession(user.id, env.SESSION_SECRET);
    return json({ token: session, user });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Minecraft sign-in failed.";
    return fail(message, 401);
  }
}

async function authGuest(request: Request, env: Env): Promise<Response> {
  if (!await rateLimit(env, "guest-auth:" + (request.headers.get("CF-Connecting-IP") ?? "unknown"), 8, 3600))
    return fail("Too many guest registrations from this network. Try later.", 429);
  const data = await body<{ deviceId?: string; deviceSecret?: string; username?: string }>(request);
  const deviceId = data?.deviceId ?? "";
  const deviceSecret = data?.deviceSecret ?? "";
  const username = cleanUsername(data?.username);
  if (!/^[a-zA-Z0-9-]{16,80}$/.test(deviceId) || deviceSecret.length < 32 || deviceSecret.length > 200)
    return fail("A valid device identity is required.");
  if (username.length < 3) return fail("Choose a username with at least 3 letters or numbers.");
  const secretHash = await sha256(deviceSecret);
  const existing = await env.DB.prepare(
    "SELECT u.id, g.secret_hash FROM guest_credentials g JOIN users u ON u.id = g.user_id WHERE g.device_id = ?"
  ).bind(deviceId).first<{ id: string; secret_hash: string }>();
  let user: User | null;
  if (existing) {
    if (existing.secret_hash !== secretHash) return fail("This device identity is already registered.", 401);
    user = await getUser(env, existing.id);
    if (user) await env.DB.prepare("UPDATE users SET last_seen = ? WHERE id = ?").bind(now(), user.id).run();
  } else {
    const userId = id();
    try {
      await env.DB.batch([
        env.DB.prepare("INSERT INTO users(id, username, auth_type, created_at, last_seen) VALUES (?, ?, 'guest', ?, ?)")
          .bind(userId, username, now(), now()),
        env.DB.prepare("INSERT INTO guest_credentials(user_id, device_id, secret_hash) VALUES (?, ?, ?)")
          .bind(userId, deviceId, secretHash),
      ]);
    } catch {
      return fail("That username is already taken. Choose another.", 409);
    }
    user = await getUser(env, userId);
  }
  if (!user) return fail("Could not create the guest profile.", 500);
  return json({ token: await createSession(user.id, env.SESSION_SECRET), user });
}

async function handleApi(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const method = request.method.toUpperCase();

  if (method === "GET" && path === "/health")
    return json({ ok: true, service: env.APP_NAME ?? "Phoenix Social", timestamp: now() });
  if (method === "POST" && path === "/v1/auth/minecraft") return authMinecraft(request, env);
  if (method === "POST" && path === "/v1/auth/guest") return authGuest(request, env);

  const auth = await requireUser(request, env);
  if (isResponse(auth)) return auth;
  const user = auth;

  if (method === "GET" && path === "/v1/me") return json({ user });

  if (method === "GET" && path === "/v1/users/search") {
    const query = (url.searchParams.get("q") ?? "").trim().slice(0, 24);
    if (query.length < 3) return json({ users: [] });
    const rows = await env.DB.prepare(
      "SELECT id, username, auth_type, minecraft_name FROM users WHERE auth_type = 'premium' AND username LIKE ? AND id <> ? LIMIT 20"
    ).bind("%" + query.replace(/[%_]/g, "") + "%", user.id).all();
    return json({ users: rows.results ?? [] });
  }

  if (method === "POST" && path === "/v1/friends/requests") {
    if (!await rateLimit(env, "friend-request:" + user.id, 20, 3600))
      return fail("Friend request limit reached. Try again later.", 429);
    const data = await body<{ toUserId?: string }>(request);
    const targetId = data?.toUserId ?? "";
    if (!targetId || targetId === user.id) return fail("Choose a valid player.");
    const target = await getUser(env, targetId);
    if (!target || target.auth_type !== "premium") return fail("Friend requests can only be sent to verified Minecraft Java accounts.", 404);
    const blocked = await env.DB.prepare(
      "SELECT 1 AS found FROM blocks WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?) LIMIT 1"
    ).bind(user.id, targetId, targetId, user.id).first();
    if (blocked) return fail("This connection is unavailable.", 403);
    const prior = await env.DB.prepare(
      "SELECT id, status FROM friend_requests WHERE from_user_id = ? AND to_user_id = ?"
    ).bind(user.id, targetId).first<{ id: string; status: string }>();
    if (prior?.status === "pending") return fail("A request is already pending.", 409);
    if (prior?.status === "accepted") return fail("You are already friends.", 409);
    const requestId = id();
    await env.DB.prepare(
      "INSERT INTO friend_requests(id, from_user_id, to_user_id, status, created_at, updated_at) VALUES (?, ?, ?, 'pending', ?, ?) ON CONFLICT(from_user_id, to_user_id) DO UPDATE SET id = excluded.id, status = 'pending', created_at = excluded.created_at, updated_at = excluded.updated_at"
    ).bind(requestId, user.id, targetId, now(), now()).run();
    return json({ ok: true, requestId }, 201);
  }

  if (method === "GET" && path === "/v1/friends/requests") {
    const rows = await env.DB.prepare(
      `SELECT r.id, r.from_user_id, r.to_user_id, r.status, r.created_at,
       a.username AS from_username, a.auth_type AS from_auth_type,
       b.username AS to_username, b.auth_type AS to_auth_type
       FROM friend_requests r
       JOIN users a ON a.id = r.from_user_id
       JOIN users b ON b.id = r.to_user_id
       WHERE (r.to_user_id = ? OR r.from_user_id = ?) AND r.status = 'pending'
       ORDER BY r.created_at DESC LIMIT 100`
    ).bind(user.id, user.id).all();
    return json({ requests: rows.results ?? [] });
  }

  const respondMatch = path.match(/^\/v1\/friends\/requests\/([0-9a-f-]+)\/respond$/i);
  if (method === "POST" && respondMatch) {
    const data = await body<{ action?: string }>(request);
    if (data?.action !== "accept" && data?.action !== "decline") return fail("Choose accept or decline.");
    const friendRequest = await env.DB.prepare(
      "SELECT id, from_user_id, to_user_id FROM friend_requests WHERE id = ? AND to_user_id = ? AND status = 'pending'"
    ).bind(respondMatch[1], user.id).first<{ id: string; from_user_id: string; to_user_id: string }>();
    if (!friendRequest) return fail("That incoming request is no longer available.", 404);
    if (data.action === "decline") {
      await env.DB.prepare("UPDATE friend_requests SET status = 'declined', updated_at = ? WHERE id = ?")
        .bind(now(), friendRequest.id).run();
      return json({ ok: true, status: "declined" });
    }
    const [a, b] = [friendRequest.from_user_id, friendRequest.to_user_id].sort();
    await env.DB.batch([
      env.DB.prepare("UPDATE friend_requests SET status = 'accepted', updated_at = ? WHERE id = ? AND status = 'pending'")
        .bind(now(), friendRequest.id),
      env.DB.prepare("INSERT OR IGNORE INTO friendships(user_a, user_b, created_at) VALUES (?, ?, ?)")
        .bind(a, b, now()),
    ]);
    return json({ ok: true, status: "accepted" });
  }

  if (method === "GET" && path === "/v1/friends") {
    const rows = await env.DB.prepare(
      `SELECT u.id, u.username, u.auth_type, u.minecraft_name, f.created_at
       FROM friendships f JOIN users u ON u.id = CASE WHEN f.user_a = ? THEN f.user_b ELSE f.user_a END
       WHERE f.user_a = ? OR f.user_b = ? ORDER BY u.username COLLATE NOCASE`
    ).bind(user.id, user.id, user.id).all();
    return json({ friends: rows.results ?? [] });
  }

  if (method === "POST" && path === "/v1/messages") {
    if (!await rateLimit(env, "messages:" + user.id, 30, 60))
      return fail("Message limit reached. Please slow down.", 429);
    const data = await body<{ toUserId?: string; message?: string }>(request);
    const recipientId = data?.toUserId ?? "";
    const message = typeof data?.message === "string" ? data.message.trim() : "";
    if (!recipientId || message.length < 1 || message.length > 500) return fail("Messages must contain 1–500 characters.");
    const [a, b] = [user.id, recipientId].sort();
    const friendship = await env.DB.prepare("SELECT 1 AS found FROM friendships WHERE user_a = ? AND user_b = ?")
      .bind(a, b).first();
    if (!friendship) return fail("You can only message an accepted Phoenix friend.", 403);
    const blocked = await env.DB.prepare(
      "SELECT 1 AS found FROM blocks WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?) LIMIT 1"
    ).bind(user.id, recipientId, recipientId, user.id).first();
    if (blocked) return fail("This conversation is unavailable.", 403);
    const recipient = await getUser(env, recipientId);
    if (!recipient) return fail("Friend not found.", 404);
    const messageId = id();
    await env.DB.prepare("INSERT INTO messages(id, sender_id, recipient_id, body, created_at) VALUES (?, ?, ?, ?, ?)")
      .bind(messageId, user.id, recipientId, message, now()).run();
    return json({ ok: true, messageId }, 201);
  }

  if (method === "GET" && path === "/v1/messages") {
    const otherId = url.searchParams.get("with") ?? "";
    const after = Math.max(0, Number(url.searchParams.get("after") ?? 0) || 0);
    if (!otherId) return fail("Choose a friend.");
    const [a, b] = [user.id, otherId].sort();
    const friendship = await env.DB.prepare("SELECT 1 AS found FROM friendships WHERE user_a = ? AND user_b = ?")
      .bind(a, b).first();
    if (!friendship) return fail("You can only view accepted friend conversations.", 403);
    const rows = await env.DB.prepare(
      `SELECT id, sender_id, recipient_id, body, created_at
       FROM messages WHERE ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?))
       AND created_at > ? ORDER BY created_at ASC LIMIT 100`
    ).bind(user.id, otherId, otherId, user.id, after).all();
    await env.DB.prepare("UPDATE messages SET read_at = ? WHERE sender_id = ? AND recipient_id = ? AND read_at IS NULL")
      .bind(now(), otherId, user.id).run();
    return json({ messages: rows.results ?? [] });
  }

  const removeFriendMatch = path.match(/^\/v1\/friends\/([0-9a-f-]+)$/i);
  if (method === "DELETE" && removeFriendMatch) {
    const otherId = removeFriendMatch[1];
    const [a, b] = [user.id, otherId].sort();
    await env.DB.batch([
      env.DB.prepare("DELETE FROM friendships WHERE user_a = ? AND user_b = ?").bind(a, b),
      env.DB.prepare("UPDATE friend_requests SET status = 'cancelled', updated_at = ? WHERE ((from_user_id = ? AND to_user_id = ?) OR (from_user_id = ? AND to_user_id = ?)) AND status = 'accepted'")
        .bind(now(), user.id, otherId, otherId, user.id),
    ]);
    return json({ ok: true });
  }

  if (method === "POST" && path === "/v1/blocks") {
    const data = await body<{ userId?: string }>(request);
    const blockedId = data?.userId ?? "";
    if (!blockedId || blockedId === user.id || !await getUser(env, blockedId)) return fail("Choose a valid player.");
    const [a, b] = [user.id, blockedId].sort();
    await env.DB.batch([
      env.DB.prepare("INSERT OR IGNORE INTO blocks(blocker_id, blocked_id, created_at) VALUES (?, ?, ?)")
        .bind(user.id, blockedId, now()),
      env.DB.prepare("DELETE FROM friendships WHERE user_a = ? AND user_b = ?").bind(a, b),
      env.DB.prepare("UPDATE friend_requests SET status = 'cancelled', updated_at = ? WHERE ((from_user_id = ? AND to_user_id = ?) OR (from_user_id = ? AND to_user_id = ?)) AND status IN ('pending', 'accepted')")
        .bind(now(), user.id, blockedId, blockedId, user.id),
    ]);
    return json({ ok: true });
  }

  const unblockMatch = path.match(/^\/v1\/blocks\/([0-9a-f-]+)$/i);
  if (method === "DELETE" && unblockMatch) {
    await env.DB.prepare("DELETE FROM blocks WHERE blocker_id = ? AND blocked_id = ?")
      .bind(user.id, unblockMatch[1]).run();
    return json({ ok: true });
  }

  if (method === "POST" && path === "/v1/reports") {
    if (!await rateLimit(env, "reports:" + user.id, 5, 86400))
      return fail("Daily report limit reached.", 429);
    const data = await body<{ userId?: string; category?: string; details?: string; messageId?: string }>(request);
    const reportedId = data?.userId ?? "";
    const categories = ["harassment", "spam", "impersonation", "inappropriate", "other"];
    const category = data?.category ?? "";
    if (!reportedId || reportedId === user.id || !categories.includes(category) || !await getUser(env, reportedId))
      return fail("Choose a valid report target and category.");
    await env.DB.prepare(
      "INSERT INTO reports(id, reporter_id, reported_id, category, details, message_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
    ).bind(id(), user.id, reportedId, category, (data?.details ?? "").slice(0, 500), data?.messageId ?? null, now()).run();
    return json({ ok: true }, 201);
  }

  return fail("Phoenix Social endpoint not found.", 404);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const headers = {
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET, POST, DELETE, OPTIONS",
      "access-control-allow-headers": "authorization, content-type",
      "access-control-max-age": "86400",
    };
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers });
    if (!env.SESSION_SECRET || env.SESSION_SECRET.length < 32)
      return json({ error: "Phoenix Social is not configured yet." }, 503, headers);
    try {
      const response = await handleApi(request, env);
      const merged = new Headers(response.headers);
      Object.entries(headers).forEach(([key, value]) => merged.set(key, value));
      return new Response(response.body, { status: response.status, headers: merged });
    } catch {
      return json({ error: "Phoenix Social encountered an internal error." }, 500, headers);
    }
  },
};
