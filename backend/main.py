"""
TONIC test backend
==================

A tiny FastAPI service that the TONIC Android client talks to:

  POST /api/login          { "username": ..., "password": ... }
        -> 200 { "token": ..., "subscriptions": [ { "name", "url" }, ... ] }
        -> 401 on bad credentials

  GET  /api/subscriptions  (header: Authorization: Bearer <token>)
        -> 200 { "subscriptions": [ { "name", "url" }, ... ] }
        -> 401 on bad/missing token

  GET  /sub/{group}        -> base64 subscription body (list of vmess:// links)
        This lets the whole flow be tested end-to-end even with no real panel:
        the app fetches this URL exactly like any v2ray subscription.

This is a TEST backend: users are in-memory, tokens are random UUIDs kept in a
dict, and the demo nodes point at fake servers (they import fine but won't
actually route traffic). Replace `USERS` and the subscription URLs with your
real panel (e.g. Marzban / x-ui) for production.

Run:
    pip install -r requirements.txt
    uvicorn main:app --host 0.0.0.0 --port 8000 --reload
"""
from __future__ import annotations

import base64
import json
import os
import uuid

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel

app = FastAPI(title="TONIC Test Backend")

# Base URL as the *phone* sees this server. For the Android emulator the host
# machine is reachable at 10.0.2.2. Override with PUBLIC_BASE_URL for a real
# device / deployment (e.g. https://api.yourbrand.com).
PUBLIC_BASE_URL = os.environ.get("PUBLIC_BASE_URL", "http://10.0.2.2:8000").rstrip("/")

# --- Demo accounts (username -> password). Replace for production. -----------
USERS = {
    "demo": "demo123",
    "user1": "pass1",
}

# Which subscriptions each user gets. The URLs point back at this service's
# /sub/{group} endpoint so the demo works with zero external setup.
USER_SUBS = {
    "demo": ["all"],
    "user1": ["all", "premium"],
}

# In-memory token store: token -> username.
TOKENS: dict[str, str] = {}


# --- Demo nodes -------------------------------------------------------------
# Each entry becomes a vmess:// link inside the subscription body. Fake servers.
DEMO_NODES = {
    "all": [
        {"ps": "TONIC · Germany", "add": "de1.example.com", "port": "443", "host": "de1.example.com"},
        {"ps": "TONIC · Netherlands", "add": "nl1.example.com", "port": "443", "host": "nl1.example.com"},
        {"ps": "TONIC · France", "add": "fr1.example.com", "port": "443", "host": "fr1.example.com"},
    ],
    "premium": [
        {"ps": "TONIC · UK (Premium)", "add": "uk1.example.com", "port": "443", "host": "uk1.example.com"},
        {"ps": "TONIC · USA (Premium)", "add": "us1.example.com", "port": "443", "host": "us1.example.com"},
    ],
}


def _vmess_link(node: dict) -> str:
    conf = {
        "v": "2",
        "ps": node["ps"],
        "add": node["add"],
        "port": node["port"],
        "id": str(uuid.uuid5(uuid.NAMESPACE_DNS, node["add"])),
        "aid": "0",
        "scy": "auto",
        "net": "ws",
        "type": "none",
        "host": node.get("host", node["add"]),
        "path": "/tonic",
        "tls": "tls",
        "sni": node.get("host", node["add"]),
    }
    raw = json.dumps(conf, ensure_ascii=False)
    return "vmess://" + base64.b64encode(raw.encode("utf-8")).decode("ascii")


def _subs_for(username: str) -> list[dict]:
    groups = USER_SUBS.get(username, ["all"])
    return [
        {"name": f"TONIC ({g})" if g != "all" else "TONIC", "url": f"{PUBLIC_BASE_URL}/sub/{g}"}
        for g in groups
    ]


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
def login(body: LoginBody):
    expected = USERS.get(body.username)
    if expected is None or expected != body.password:
        raise HTTPException(status_code=401, detail="bad credentials")
    token = uuid.uuid4().hex
    TOKENS[token] = body.username
    return {"token": token, "subscriptions": _subs_for(body.username)}


@app.get("/api/subscriptions")
def subscriptions(username: str = Depends(_current_user)):
    return {"subscriptions": _subs_for(username)}


@app.get("/sub/{group}", response_class=PlainTextResponse)
def sub(group: str):
    nodes = DEMO_NODES.get(group)
    if nodes is None:
        raise HTTPException(status_code=404, detail="unknown group")
    body = "\n".join(_vmess_link(n) for n in nodes)
    # v2ray subscriptions are base64-encoded newline-separated links.
    return base64.b64encode(body.encode("utf-8")).decode("ascii")


@app.get("/")
def root():
    return {"service": "TONIC Test Backend", "public_base_url": PUBLIC_BASE_URL}
