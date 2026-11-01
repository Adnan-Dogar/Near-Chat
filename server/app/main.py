"""Near-Chat single-instance self-hosted messaging API, alpha release.

This service does NOT provide end-to-end encryption, persistent WebSocket fan-out,
push notifications, account recovery, or production-grade moderation.
"""
import asyncio
import collections
import json
import os
import re
import time
import uuid
from contextlib import asynccontextmanager
from datetime import datetime
from pathlib import Path
from typing import Annotated

import asyncpg
from fastapi import FastAPI, Depends, Header, HTTPException, Request, WebSocket, WebSocketDisconnect, status
from pydantic import BaseModel, Field, field_validator
from starlette.websockets import WebSocketState
from .security import hash_password, check_password, make_token, parse_token

USERNAME = re.compile(r"^[a-z0-9_]{3,24}$")
SECRET = os.getenv("JWT_SECRET", "")
DATABASE_URL = os.getenv("DATABASE_URL", "")
if not SECRET or len(SECRET) < 32 or not DATABASE_URL:
    raise RuntimeError("Set DATABASE_URL and a random JWT_SECRET of at least 32 characters")

# Lightweight process-local throttling, not a substitute for distributed edge rate limiting.
_limits: dict[str, collections.deque] = collections.defaultdict(collections.deque)
_connections: dict[uuid.UUID, set[WebSocket]] = collections.defaultdict(set)


def throttle(key: str, max_events: int, period: int) -> None:
    now = time.monotonic()
    q = _limits[key]
    while q and now - q[0] > period:
        q.popleft()
    if len(q) >= max_events:
        raise HTTPException(429, "Too many requests; try again later")
    q.append(now)


@asynccontextmanager
async def lifespan(app: FastAPI):
    pool = await asyncpg.create_pool(DATABASE_URL, min_size=1, max_size=12,
                                     command_timeout=15)
    async with pool.acquire() as conn:
        await conn.execute(Path(__file__).with_name("schema.sql").read_text())
    app.state.pool = pool
    try:
        yield
    finally:
        await pool.close()


app = FastAPI(title="Near-Chat API", version="0.7.0", lifespan=lifespan,
              docs_url=None, redoc_url=None, openapi_url=None)


class Credentials(BaseModel):
    username: str = Field(min_length=3, max_length=24)
    password: str = Field(min_length=12, max_length=128)

    @field_validator("username")
    @classmethod
    def clean_username(cls, name: str) -> str:
        name = name.lower()
        if not USERNAME.fullmatch(name):
            raise ValueError("Username must be 3–24 lowercase letters, numbers or _")
        return name


class Signup(Credentials):
    display_name: str = Field(min_length=2, max_length=40)


class Direct(BaseModel):
    username: str = Field(min_length=3, max_length=24)


class Group(BaseModel):
    title: str = Field(min_length=2, max_length=70)
    usernames: list[str] = Field(min_length=1, max_length=30)


class MessageIn(BaseModel):
    body: str = Field(min_length=1, max_length=4000)
    client_id: uuid.UUID


class Report(BaseModel):
    username: str
    reason: str = Field(min_length=10, max_length=500)


async def require_user(request: Request, authorization: Annotated[str | None, Header()] = None) -> uuid.UUID:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Authentication required")
    uid = parse_token(authorization[7:], SECRET)
    if not uid:
        raise HTTPException(401, "Invalid or expired session")
    return uid


async def require_member(conn, chat_id: uuid.UUID, user_id: uuid.UUID):
    member = await conn.fetchval("SELECT 1 FROM memberships WHERE chat_id=$1 AND user_id=$2", chat_id, user_id)
    if not member:
        raise HTTPException(404, "Conversation not found")


def user_out(row) -> dict:
    return {"id": str(row["id"]), "username": row["username"], "display_name": row["display_name"]}


def message_out(row) -> dict:
    return {"id": str(row["id"]), "chat_id": str(row["chat_id"]),
            "sender_id": str(row["sender_id"]), "body": row["body"],
            "created_at": row["created_at"].isoformat()}


@app.get("/healthz")
async def health(request: Request):
    await request.app.state.pool.fetchval("SELECT 1")
    return {"ok": True}


@app.post("/v1/auth/register", status_code=201)
async def register(data: Signup, request: Request):
    throttle("register:" + (request.client.host if request.client else "unknown"), 5, 3600)
    uid = uuid.uuid4()
    async with request.app.state.pool.acquire() as conn:
        try:
            await conn.execute("INSERT INTO users(id,username,display_name,password_hash) VALUES($1,$2,$3,$4)",
                               uid, data.username, data.display_name.strip(), hash_password(data.password))
        except asyncpg.UniqueViolationError:
            raise HTTPException(409, "Username unavailable")
    return {"token": make_token(uid, SECRET), "user": {"id": str(uid), "username": data.username, "display_name": data.display_name.strip()}}


@app.post("/v1/auth/login")
async def login(data: Credentials, request: Request):
    throttle("login:" + (request.client.host if request.client else "unknown"), 15, 300)
    async with request.app.state.pool.acquire() as conn:
        row = await conn.fetchrow("SELECT * FROM users WHERE username=$1", data.username)
    if not row or not check_password(data.password, row["password_hash"]):
        raise HTTPException(401, "Incorrect username or password")
    return {"token": make_token(row["id"], SECRET), "user": user_out(row)}


@app.get("/v1/me")
async def me(request: Request, uid: uuid.UUID = Depends(require_user)):
    async with request.app.state.pool.acquire() as conn:
        row = await conn.fetchrow("SELECT * FROM users WHERE id=$1", uid)
    if not row:
        raise HTTPException(401, "Account no longer exists")
    return user_out(row)


@app.get("/v1/users/{username}")
async def lookup(username: str, request: Request, uid: uuid.UUID = Depends(require_user)):
    if not USERNAME.fullmatch(username.lower()):
        raise HTTPException(404, "User not found")
    async with request.app.state.pool.acquire() as conn:
        row = await conn.fetchrow("SELECT id,username,display_name FROM users WHERE username=$1", username.lower())
    if not row:
        raise HTTPException(404, "User not found")
    return user_out(row)


@app.post("/v1/chats/direct")
async def direct(data: Direct, request: Request, uid: uuid.UUID = Depends(require_user)):
    username = data.username.lower()
    async with request.app.state.pool.acquire() as conn:
        async with conn.transaction():
            target = await conn.fetchrow("SELECT id FROM users WHERE username=$1", username)
            if not target or target["id"] == uid:
                raise HTTPException(404, "Contact not found")
            blocked = await conn.fetchval("SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1)", uid, target["id"])
            if blocked:
                raise HTTPException(403, "This conversation is unavailable")
            # A transaction-level advisory lock serializes competing direct-chat creation.
            pair = sorted([str(uid), str(target["id"])])
            await conn.execute("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", "direct:" + ":".join(pair))
            existing = await conn.fetchval("""SELECT c.id FROM conversations c JOIN memberships m1 ON m1.chat_id=c.id AND m1.user_id=$1
                 JOIN memberships m2 ON m2.chat_id=c.id AND m2.user_id=$2 WHERE c.kind='direct' LIMIT 1""", uid, target["id"])
            if existing:
                return {"id": str(existing)}
            chat = uuid.uuid4()
            await conn.execute("INSERT INTO conversations(id,kind,created_by) VALUES($1,'direct',$2)", chat, uid)
            await conn.executemany("INSERT INTO memberships(chat_id,user_id) VALUES($1,$2)", [(chat, uid), (chat,target["id"])])
    await publish([uid, target["id"]], {"type": "chat_created", "chat_id": str(chat)})
    return {"id": str(chat)}


@app.post("/v1/chats/group", status_code=201)
async def create_group(data: Group, request: Request, uid: uuid.UUID = Depends(require_user)):
    names = list(dict.fromkeys(x.lower().strip() for x in data.usernames))
    if any(not USERNAME.fullmatch(n) for n in names):
        raise HTTPException(400, "Invalid username in group")
    async with request.app.state.pool.acquire() as conn:
        async with conn.transaction():
            rows = await conn.fetch("SELECT id,username FROM users WHERE username=ANY($1::text[])", names)
            if len(rows) != len(names):
                raise HTTPException(404, "A group participant was not found")
            members = list({uid, *(r["id"] for r in rows)})
            if len(members) < 2:
                raise HTTPException(400, "Add at least one other participant")
            chat = uuid.uuid4()
            await conn.execute("INSERT INTO conversations(id,kind,title,created_by) VALUES($1,'group',$2,$3)", chat, data.title.strip(), uid)
            await conn.executemany("INSERT INTO memberships(chat_id,user_id) VALUES($1,$2)", [(chat, u) for u in members])
    await publish(members, {"type": "chat_created", "chat_id": str(chat)})
    return {"id": str(chat)}


@app.get("/v1/chats")
async def chats(request: Request, uid: uuid.UUID = Depends(require_user)):
    async with request.app.state.pool.acquire() as conn:
        rows = await conn.fetch("""SELECT c.id,c.kind,c.title,c.created_at,
          COALESCE((SELECT string_agg(u.display_name, ', ' ORDER BY u.username)
             FROM memberships m JOIN users u ON u.id=m.user_id WHERE m.chat_id=c.id AND m.user_id<>$1),'') peer_names,
          (SELECT body FROM messages WHERE chat_id=c.id ORDER BY created_at DESC LIMIT 1) last_message
          FROM conversations c JOIN memberships mine ON mine.chat_id=c.id AND mine.user_id=$1
          ORDER BY COALESCE((SELECT max(created_at) FROM messages WHERE chat_id=c.id),c.created_at) DESC LIMIT 100""", uid)
    return [{"id": str(r["id"]), "kind": r["kind"], "title": r["title"] if r["kind"]=="group" else r["peer_names"],
             "last_message": r["last_message"] or ""} for r in rows]


@app.get("/v1/chats/{chat_id}/messages")
async def messages(chat_id: uuid.UUID, request: Request, before: datetime | None = None, uid: uuid.UUID = Depends(require_user)):
    async with request.app.state.pool.acquire() as conn:
        await require_member(conn, chat_id, uid)
        rows = await conn.fetch("""SELECT * FROM messages WHERE chat_id=$1 AND ($2::timestamptz IS NULL OR created_at<$2)
                  ORDER BY created_at DESC LIMIT 100""", chat_id, before)
    return [message_out(r) for r in reversed(rows)]


@app.post("/v1/chats/{chat_id}/messages", status_code=201)
async def send(chat_id: uuid.UUID, data: MessageIn, request: Request, uid: uuid.UUID = Depends(require_user)):
    throttle(f"send:{uid}", 90, 60)
    async with request.app.state.pool.acquire() as conn:
        async with conn.transaction():
            await require_member(conn, chat_id, uid)
            recipients = await conn.fetch("SELECT user_id FROM memberships WHERE chat_id=$1", chat_id)
            # Disallow messages if participants blocked each other in direct chats.
            kind = await conn.fetchval("SELECT kind FROM conversations WHERE id=$1", chat_id)
            if kind == "direct":
                other = next((r["user_id"] for r in recipients if r["user_id"] != uid), None)
                if other and await conn.fetchval("SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1)", uid,other):
                    raise HTTPException(403, "Conversation unavailable")
            row = await conn.fetchrow("""INSERT INTO messages(id,chat_id,sender_id,body,client_id)
                    VALUES($1,$2,$3,$4,$5) ON CONFLICT(chat_id,sender_id,client_id) DO UPDATE SET body=messages.body
                    RETURNING *""",uuid.uuid4(),chat_id,uid,data.body.strip(),data.client_id)
    payload = message_out(row)
    await publish([x["user_id"] for x in recipients], {"type":"message", "message": payload})
    return payload


@app.post("/v1/blocks/{username}")
async def block(username: str, request: Request, uid: uuid.UUID = Depends(require_user)):
    async with request.app.state.pool.acquire() as conn:
        other = await conn.fetchval("SELECT id FROM users WHERE username=$1", username.lower())
        if not other or other==uid:
            raise HTTPException(404, "User not found")
        await conn.execute("INSERT INTO blocks(blocker_id,blocked_id) VALUES($1,$2) ON CONFLICT DO NOTHING", uid, other)
    return {"ok": True}


@app.post("/v1/reports", status_code=201)
async def report(data: Report, request: Request, uid: uuid.UUID = Depends(require_user)):
    throttle(f"report:{uid}", 10, 86400)
    async with request.app.state.pool.acquire() as conn:
        other = await conn.fetchval("SELECT id FROM users WHERE username=$1", data.username.lower())
        if not other or other == uid:
            raise HTTPException(404, "User not found")
        await conn.execute("INSERT INTO reports(id,reporter_id,reported_user_id,reason) VALUES($1,$2,$3,$4)", uuid.uuid4(),uid,other,data.reason.strip())
    return {"ok": True}


async def publish(users, payload: dict):
    """Single worker only. Multi-worker deployment requires Redis pub/sub."""
    data = json.dumps(payload)
    for user_id in set(users):
        for ws in list(_connections.get(user_id, [])):
            try:
                await ws.send_text(data)
            except Exception:
                _connections[user_id].discard(ws)


@app.websocket("/v1/ws")
async def websocket(ws: WebSocket):
    await ws.accept()
    uid = None
    try:
        # Authenticate in the first frame, not in a URL that may be logged.
        frame = await asyncio.wait_for(ws.receive_json(), timeout=5)
        uid = parse_token(frame.get("token", "") if isinstance(frame,dict) else "", SECRET)
        if uid is None:
            await ws.close(code=1008)
            return
        _connections[uid].add(ws)
        await ws.send_json({"type":"connected"})
        while True:
            message = await ws.receive_text()
            if len(message)>1024:
                await ws.close(code=1009)
                break
            # Do not trust client-sent chat events; only REST endpoints create events.
            if message == "ping":
                await ws.send_text('{"type":"pong"}')
    except (WebSocketDisconnect, asyncio.TimeoutError, ValueError, RuntimeError):
        pass
    finally:
        if uid is not None:
            _connections[uid].discard(ws)
