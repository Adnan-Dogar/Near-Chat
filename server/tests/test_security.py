import uuid
from app.security import hash_password, check_password, make_token, parse_token

def test_password_roundtrip():
    password = 'a 12-char secret 123!'
    digest = hash_password(password)
    assert digest != password and check_password(password, digest)
    assert not check_password('wrong-password',digest)

def test_signed_token():
    secret = 'test-secret-that-is-at-least-32-characters'
    user = uuid.uuid4()
    token = make_token(str(user),secret)
    assert parse_token(token,secret)==user
    assert parse_token(token,'another-invalid-secret-with-32-chars') is None
