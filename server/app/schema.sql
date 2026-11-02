CREATE TABLE IF NOT EXISTS users (
 id UUID PRIMARY KEY, username TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL,
 password_hash TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS conversations (
 id UUID PRIMARY KEY, kind TEXT NOT NULL CHECK (kind IN ('direct','group')),
 title TEXT NOT NULL DEFAULT '', created_by UUID NOT NULL REFERENCES users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS memberships (
 chat_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 joined_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (chat_id,user_id)
);
CREATE INDEX IF NOT EXISTS memberships_user_idx ON memberships(user_id);
CREATE TABLE IF NOT EXISTS messages (
 id UUID PRIMARY KEY, chat_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
 sender_id UUID NOT NULL REFERENCES users(id), body TEXT NOT NULL,
 client_id UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(chat_id, sender_id, client_id)
);
CREATE INDEX IF NOT EXISTS messages_chat_idx ON messages(chat_id, created_at DESC, id DESC);
CREATE TABLE IF NOT EXISTS blocks (
 blocker_id UUID NOT NULL REFERENCES users(id), blocked_id UUID NOT NULL REFERENCES users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(blocker_id,blocked_id)
);
CREATE TABLE IF NOT EXISTS reports (
 id UUID PRIMARY KEY, reporter_id UUID NOT NULL REFERENCES users(id),
 reported_user_id UUID NOT NULL REFERENCES users(id), reason TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Idempotent alpha migration for v0.8; do not drop existing chats.
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio TEXT NOT NULL DEFAULT '';
ALTER TABLE messages ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
CREATE TABLE IF NOT EXISTS reactions (
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  emoji TEXT NOT NULL,
  PRIMARY KEY(message_id,user_id)
);
CREATE TABLE IF NOT EXISTS chat_preferences (
  chat_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  muted BOOLEAN NOT NULL DEFAULT false,
  pinned BOOLEAN NOT NULL DEFAULT false,
  PRIMARY KEY(chat_id,user_id)
);
