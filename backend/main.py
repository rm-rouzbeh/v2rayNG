"""
TONIC test backend
==================

A tiny FastAPI service that the TONIC Android client talks to:

  POST /api/login          { "username": ..., "password": ... }
        -> 200 { "token": ..., "subscriptions": [ { "name", "url" }, ... ] }
        -> 401 on bad credentials

  GET  /api/subscriptions  (header: Authorization: Bearer <token>)
        -> 200 { "subscriptions": [ { "name", "url" }, ... ] }

  GET  /sub/{group}        -> base64 subscription body (list of vless:// links)
        The app fetches this URL exactly like any v2ray subscription.

The subscription URL returned to the app is derived from the request host, so
it works whether the phone reaches this server at 10.0.2.2 (emulator),
192.168.x.x (LAN) or a real domain — no env var needed.

Run:
    pip install -r requirements.txt
    uvicorn main:app --host 0.0.0.0 --port 8000 --reload
"""
from __future__ import annotations

import base64
import uuid

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel

app = FastAPI(title="TONIC Test Backend")

# --- Demo accounts (username -> password). Replace for production. -----------
USERS = {
    "demo": "demo123",
    "user1": "pass1",
}

# Which subscription groups each user gets.
USER_SUBS = {
    "demo": ["all"],
    "user1": ["all"],
}

# In-memory token store: token -> username.
TOKENS: dict[str, str] = {}

# --- Real subscription groups -----------------------------------------------
# Each group is a list of real share links (vless:// / vmess:// / ...). The app
# imports every link in the group and auto-picks the fastest. Paste your own
# links here (one per line) to change what the app receives.
#
# Alternatively, to point the app straight at your real panel subscription
# instead of serving links from here, set REAL_SUB_URL below and it will be
# returned by /api/login (the app will fetch that URL directly).
REAL_SUB_URL = ""  # e.g. "https://user.tonicserver.com/user/djMsMTQ3MSwxNzgz..."

SUB_GROUPS: dict[str, list[str]] = {
    "all": """
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@irdl.tonicserver.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=de.tonicserver.com#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%AE%F0%9F%87%B7%F0%9F%87%A9%F0%9F%87%AA%20-%20GERMANY
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@irdl.tonicserver.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=uk.tonicserver.com#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%AE%F0%9F%87%B7%F0%9F%87%AC%F0%9F%87%A7%20-%20UK
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@irdl.tonicserver.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=nl.tonicserver.com#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%AE%F0%9F%87%B7%F0%9F%87%B3%F0%9F%87%B1%20-%20NETHERLANDS
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@irdl.tonicserver.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=tr.tonicserver.com#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%AE%F0%9F%87%B7%F0%9F%87%B9%F0%9F%87%B7%20-%20TURKEY
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@irdl.tonicserver.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=ae.tonicserver.com#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%AE%F0%9F%87%B7%F0%9F%87%A6%F0%9F%87%AA%20-%20UAE
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@irdl.tonicserver.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=fr.tonicserver.com#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%AE%F0%9F%87%B7%F0%9F%87%AB%F0%9F%87%B7%20-%20FRANCE
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@irdl.tonicserver.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=it.tonicserver.com#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%AE%F0%9F%87%B7%F0%9F%87%AE%F0%9F%87%B9%20-%20ITALY
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@irdl.tonicserver.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=kw.tonicserver.com#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%AE%F0%9F%87%B7%F0%9F%87%B0%F0%9F%87%BC%20-%20KUWAIT
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@irdl.tonicserver.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=ch.tonicserver.com#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%AE%F0%9F%87%B7%F0%9F%87%A8%F0%9F%87%AD%20-%20SWITZERLAND
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@irdl.tonicserver.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=us.tonicserver.com#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%AE%F0%9F%87%B7%F0%9F%87%BA%F0%9F%87%B8%20-%20USA
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@irdl.tonicserver.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=ca.tonicserver.com#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%AE%F0%9F%87%B7%F0%9F%87%A8%F0%9F%87%A6%20-%20CANADA
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@irdl.tonicserver.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=ua.tonicserver.com#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%AE%F0%9F%87%B7%F0%9F%87%BA%F0%9F%87%A6%20-%20UKRAINE
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@res.7onic.com:80?encryption=none&security=none&type=xhttp&headerType=none&mode=auto#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%A9%F0%9F%87%AA%20-%20Direct%201%EF%B8%8F%E2%83%A3%F0%9F%86%98
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@fl.7onic.com:80?encryption=none&security=none&type=xhttp&headerType=none&host=lazylay.global.ssl.fastly.net&mode=auto#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%A9%F0%9F%87%AA%20-%20Direct%202%EF%B8%8F%E2%83%A3%F0%9F%86%98
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@ndc.7onic.com:443?encryption=none&security=tls&type=xhttp&headerType=none&mode=auto&sni=drc.7onic.com&fp=chrome&alpn=h2#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%A9%F0%9F%87%AA%20-%20Direct%203%EF%B8%8F%E2%83%A3%F0%9F%86%98
vless://a4cdcde3-0863-49e0-9766-2c9498ecf7cd@ndc.7onic.com:2053?encryption=none&security=tls&type=ws&headerType=none&path=%2F&sni=drc.7onic.com&fp=android&alpn=h2#%F0%9F%8D%BETONIC%20%7C%20%F0%9F%87%A9%F0%9F%87%AA%20-%20Direct%204%EF%B8%8F%E2%83%A3%F0%9F%86%98
""".strip().splitlines(),
}


def _sub_links(group: str) -> list[str]:
    return [ln.strip() for ln in SUB_GROUPS.get(group, []) if ln.strip()]


def _subs_for(username: str, base_url: str) -> list[dict]:
    if REAL_SUB_URL:
        return [{"name": "TONIC", "url": REAL_SUB_URL}]
    groups = USER_SUBS.get(username, ["all"])
    base = base_url.rstrip("/")
    return [{"name": "TONIC", "url": f"{base}/sub/{g}"} for g in groups]


class LoginBody(BaseModel):
    username: str
    password: str


def _current_user(authorization: str | None = Header(default=None)) -> str:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="missing token")
    token = authorization.split(" ", 1)[1].strip()
    username = TOKENS.get(token)
    if not username:
        raise HTTPException(status_code=401, detail="invalid token")
    return username


@app.post("/api/login")
def login(body: LoginBody, request: Request):
    expected = USERS.get(body.username)
    if expected is None or expected != body.password:
        raise HTTPException(status_code=401, detail="bad credentials")
    token = uuid.uuid4().hex
    TOKENS[token] = body.username
    return {"token": token, "subscriptions": _subs_for(body.username, str(request.base_url))}


@app.get("/api/subscriptions")
def subscriptions(request: Request, username: str = Depends(_current_user)):
    return {"subscriptions": _subs_for(username, str(request.base_url))}


@app.get("/sub/{group}", response_class=PlainTextResponse)
def sub(group: str):
    links = _sub_links(group)
    if not links:
        raise HTTPException(status_code=404, detail="unknown group")
    body = "\n".join(links)
    # v2ray subscriptions are base64-encoded newline-separated links.
    return base64.b64encode(body.encode("utf-8")).decode("ascii")


@app.get("/")
def root():
    return {"service": "TONIC Test Backend"}
