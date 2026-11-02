"""Fast API route and validation tests requiring no live database."""
import importlib
import os
from pydantic import ValidationError


def test_contract():
    os.environ.setdefault("JWT_SECRET", "alpha-testing-local-only-secret-1234567890123456")
    os.environ.setdefault("DATABASE_URL", "postgresql://placeholder:password@localhost/db")
    app_module = importlib.import_module("app.main")
    paths = {(route.path, next(iter(route.methods))) for route in app_module.app.routes if getattr(route, "methods", None)}
    present = {p for p, _ in paths}
    for path in ("/healthz", "/v1/auth/login", "/v1/auth/register", "/v1/me",
                 "/v1/chats", "/v1/chats/{chat_id}/options", "/v1/chats/{chat_id}/search",
                 "/v1/chats/{chat_id}/messages/{message_id}",
                 "/v1/chats/{chat_id}/messages/{message_id}/reaction",
                 "/v1/blocks/{username}", "/v1/reports"):
        assert path in present
    assert app_module.Reaction(emoji="❤️").emoji == "❤️"
    try:
        app_module.Reaction(emoji="random-reaction-longer")
    except ValidationError:
        pass
    else:
        raise AssertionError("Reaction length validation absent")


def test_message_serialization():
    os.environ.setdefault("JWT_SECRET", "alpha-testing-local-only-secret-1234567890123456")
    os.environ.setdefault("DATABASE_URL", "postgresql://placeholder:password@localhost/db")
    from app.main import message_out
    from datetime import datetime, timezone
    import uuid
    now = datetime.now(timezone.utc)
    row = {"id":uuid.uuid4(),"chat_id":uuid.uuid4(),"sender_id":uuid.uuid4(),
           "body":"hello","created_at":now,"edited_at":None,"deleted_at":None,
           "reactions":'[{' + '"emoji":"👍","username":"tester"' + '}]'}
    output = message_out(row)
    assert output["body"] == "hello"
    assert output["reactions"][0]["emoji"] == "👍"
    assert output["edited_at"] is None
