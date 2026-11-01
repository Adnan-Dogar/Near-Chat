"""Password hashing and short-lived signed bearer access tokens."""
import os
import time
import uuid
import jwt
from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError, VerificationError, InvalidHashError

_hasher = PasswordHasher(time_cost=3, memory_cost=65536, parallelism=2)


def hash_password(password: str) -> str:
    return _hasher.hash(password)


def check_password(password: str, stored: str) -> bool:
    try:
        return _hasher.verify(stored, password)
    except (VerifyMismatchError, VerificationError, InvalidHashError):
        return False


def make_token(user_id: str, secret: str) -> str:
    now = int(time.time())
    return jwt.encode({"sub": str(user_id), "iat": now, "exp": now + 12 * 3600,
                       "iss": "near-chat", "jti": str(uuid.uuid4())}, secret, algorithm="HS256")


def parse_token(token: str, secret: str) -> uuid.UUID | None:
    try:
        claims = jwt.decode(token, secret, algorithms=["HS256"], issuer="near-chat", options={"require": ["exp", "sub", "iss"]})
        return uuid.UUID(claims["sub"])
    except (jwt.InvalidTokenError, KeyError, ValueError, TypeError):
        return None
