PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  username TEXT NOT NULL COLLATE NOCASE UNIQUE,
  auth_type TEXT NOT NULL CHECK (auth_type IN ('premium', 'guest')),
  minecraft_uuid TEXT UNIQUE,
  minecraft_name TEXT,
  created_at INTEGER NOT NULL,
  last_seen INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS guest_credentials (
  user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  device_id TEXT NOT NULL UNIQUE,
  secret_hash TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS friend_requests (
  id TEXT PRIMARY KEY,
  from_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  to_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL CHECK (status IN ('pending', 'accepted', 'declined', 'cancelled')),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  CHECK (from_user_id <> to_user_id),
  UNIQUE (from_user_id, to_user_id)
);

CREATE TABLE IF NOT EXISTS friendships (
  user_a TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  user_b TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  CHECK (user_a < user_b),
  PRIMARY KEY (user_a, user_b)
);

CREATE TABLE IF NOT EXISTS messages (
  id TEXT PRIMARY KEY,
  sender_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body TEXT NOT NULL CHECK (length(body) BETWEEN 1 AND 500),
  created_at INTEGER NOT NULL,
  read_at INTEGER,
  CHECK (sender_id <> recipient_id)
);

CREATE TABLE IF NOT EXISTS blocks (
  blocker_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  CHECK (blocker_id <> blocked_id),
  PRIMARY KEY (blocker_id, blocked_id)
);

CREATE TABLE IF NOT EXISTS reports (
  id TEXT PRIMARY KEY,
  reporter_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  reported_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category TEXT NOT NULL,
  details TEXT NOT NULL DEFAULT '',
  message_id TEXT REFERENCES messages(id) ON DELETE SET NULL,
  created_at INTEGER NOT NULL,
  CHECK (reporter_id <> reported_id)
);

CREATE TABLE IF NOT EXISTS rate_limits (
  rate_key TEXT PRIMARY KEY,
  window_start INTEGER NOT NULL,
  request_count INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_requests_incoming ON friend_requests(to_user_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_requests_outgoing ON friend_requests(from_user_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_messages_pair_time ON messages(sender_id, recipient_id, created_at);
CREATE INDEX IF NOT EXISTS idx_reports_created ON reports(created_at);
