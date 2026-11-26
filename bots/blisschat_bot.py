"""
blisschat bot SDK (Python 3.9+).  pip install -r requirements.txt

A bot is an ordinary blisschat account whose username ends with "_bot". You create it in the app
("+" -> Бот) and get a token like  my_cool_bot:Ab3dE...  .  Messages of ordinary chats, groups and channels are
plain JSON now (end-to-end encryption exists only in opt-in "secret chats", which bots do not join), so a bot
just reads and writes messages. Run it on any always-on machine (a VPS, Render, Railway, botkeep.cloud, a Raspberry Pi...)
- just like a Telegram bot. Only photos/stickers keep a per-file key (see send_image).

    from blisschat_bot import Bot
    bot = Bot("my_cool_bot:TOKEN")

    @bot.command("start")
    def start(ctx):
        ctx.reply("Привет! Я бот.")

    @bot.on_message
    def echo(ctx):
        ctx.reply("Вы написали: " + ctx.text)

    bot.run()
"""
from __future__ import annotations

import base64
import json
import os
import time
import uuid
from dataclasses import dataclass
from typing import Any, Callable, Dict, List, Optional

import requests
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

API_KEY = "AIzaSyBUXc71LMSwk0Nb82bieZFZbGeMtJ5uU2s"   # Firebase web API key of the blisschat project (public by design)
PROJECT = "bitrate-71684"
BOT_EMAIL_DOMAIN = "bots.blisschat.app"
WORKER_URL = "https://blisschat-push.blisschat-relay.workers.dev"   # sends the push notification to the recipients

# ----------------------------------------------------------------------------------------------- crypto
# Messages are plain JSON. Only media files are sealed with a random per-file key that travels inside the message
# (same as the Android app: data/crypto/Crypto.kt).


def b64(b: bytes) -> str:
    return base64.b64encode(b).decode()


def unb64(s: str) -> bytes:
    return base64.b64decode(s)


def seal(key: bytes, plain: bytes, aad: Optional[bytes] = None) -> bytes:
    iv = os.urandom(12)
    return iv + AESGCM(key).encrypt(iv, plain, aad)          # iv(12) || ciphertext || tag(16)


def open_(key: bytes, blob: bytes, aad: Optional[bytes] = None) -> bytes:
    return AESGCM(key).decrypt(blob[:12], blob[12:], aad)


# ----------------------------------------------------------------------------------------------- Firestore REST


def _enc(v: Any) -> dict:
    if v is None:
        return {"nullValue": None}
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, int):
        return {"integerValue": str(v)}
    if isinstance(v, float):
        return {"doubleValue": v}
    if isinstance(v, bytes):
        return {"bytesValue": b64(v)}
    if isinstance(v, str):
        return {"stringValue": v}
    if isinstance(v, (list, tuple)):
        return {"arrayValue": {"values": [_enc(x) for x in v]}}
    if isinstance(v, dict):
        return {"mapValue": {"fields": {k: _enc(x) for k, x in v.items()}}}
    raise TypeError(type(v))


def _dec(v: dict) -> Any:
    (t, x), = v.items()
    if t == "nullValue":
        return None
    if t == "integerValue":
        return int(x)
    if t == "arrayValue":
        return [_dec(i) for i in x.get("values", [])]
    if t == "mapValue":
        return {k: _dec(i) for k, i in x.get("fields", {}).items()}
    if t == "bytesValue":
        return unb64(x)
    return x


def _doc(d: dict) -> dict:
    out = {k: _dec(v) for k, v in d.get("fields", {}).items()}
    out["_id"] = d["name"].rsplit("/", 1)[-1]
    return out


class BotError(Exception):
    pass


@dataclass
class Context:
    """What your handler receives for every incoming message."""
    bot: "Bot"
    chat: dict
    sender_id: str
    text: str
    kind: str
    raw: dict
    message_id: str = ""

    @property
    def chat_id(self) -> str:
        return self.chat["_id"]

    @property
    def is_private(self) -> bool:
        return self.chat.get("type") == "DIRECT"

    @property
    def command(self) -> Optional[str]:
        t = self.text.strip()
        return t[1:].split()[0].split("@")[0].lower() if t.startswith("/") and len(t) > 1 else None

    @property
    def args(self) -> List[str]:
        return self.text.strip().split()[1:]

    def reply(self, text: str, quote: bool = False) -> None:
        """Sends text to the chat. quote=True answers with a "reply" to the incoming message."""
        self.bot.send(self.chat_id, text, reply_to=self.message_id if quote else "")

    def reply_photo(self, data: bytes, width: int = 0, height: int = 0) -> None:
        self.bot.send_image(self.chat_id, data, width, height)


class Bot:
    def __init__(self, token: str, key_file: Optional[str] = None, poll_interval: float = 1.5):
        try:
            self.username, self._password = token.strip().split(":", 1)
        except ValueError:
            raise BotError("Токен должен выглядеть как  имя_bot:пароль")
        self.email = f"{self.username}@{BOT_EMAIL_DOMAIN}"
        self.poll = poll_interval
        self.key_file = key_file          # kept only for backwards compatibility, no longer used
        self._s = requests.Session()
        self._id_token = ""
        self._refresh = ""
        self._exp = 0.0
        self.uid = ""
        self._handlers: List[Callable[[Context], None]] = []
        self._commands: Dict[str, Callable[[Context], None]] = {}
        self._seen: Dict[str, int] = {}
        self._start = int(time.time() * 1000)

    # ------------------------------------------------------------------ decorators
    def on_message(self, fn: Callable[[Context], None]):
        self._handlers.append(fn)
        return fn

    def command(self, name: str):
        def deco(fn):
            self._commands[name.lower().lstrip("/")] = fn
            return fn
        return deco

    # ------------------------------------------------------------------ auth
    def _login(self) -> None:
        r = self._s.post(
            f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={API_KEY}",
            json={"email": self.email, "password": self._password, "returnSecureToken": True}, timeout=20)
        if r.status_code != 200:
            raise BotError("Не удалось войти: проверьте токен бота (" + r.json().get("error", {}).get("message", str(r.status_code)) + ")")
        self._store(r.json())

    def _store(self, j: dict) -> None:
        self._id_token = j.get("idToken") or j.get("id_token")
        self._refresh = j.get("refreshToken") or j.get("refresh_token")
        self.uid = j.get("localId") or j.get("user_id") or self.uid
        self._exp = time.time() + int(j.get("expiresIn") or j.get("expires_in") or 3600) - 120

    def _token(self) -> str:
        if time.time() >= self._exp:
            r = self._s.post(f"https://securetoken.googleapis.com/v1/token?key={API_KEY}",
                             data={"grant_type": "refresh_token", "refresh_token": self._refresh}, timeout=20)
            if r.status_code != 200:
                self._login()
            else:
                self._store(r.json())
        return self._id_token

    # ------------------------------------------------------------------ Firestore
    @property
    def _base(self) -> str:
        return f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/(default)/documents"

    def _req(self, method: str, url: str, **kw) -> requests.Response:
        for attempt in range(4):
            r = self._s.request(method, url, headers={"Authorization": "Bearer " + self._token()}, timeout=30, **kw)
            if r.status_code in (429, 500, 503):
                time.sleep(1 + attempt)
                continue
            return r
        return r

    def _get(self, path: str) -> Optional[dict]:
        r = self._req("GET", f"{self._base}/{path}")
        if r.status_code == 404:
            return None
        if r.status_code != 200:
            raise BotError(f"GET {path}: {r.status_code} {r.text[:200]}")
        return _doc(r.json())

    def _query(self, parent: str, q: dict) -> List[dict]:
        url = f"{self._base}/{parent}:runQuery" if parent else f"{self._base}:runQuery"
        r = self._req("POST", url, json={"structuredQuery": q})
        if r.status_code != 200:
            raise BotError(f"query: {r.status_code} {r.text[:200]}")
        return [_doc(x["document"]) for x in r.json() if "document" in x]

    def _commit(self, writes: List[dict]) -> None:
        r = self._req("POST", f"{self._base}:commit", json={"writes": writes})
        if r.status_code != 200:
            raise BotError(f"commit: {r.status_code} {r.text[:300]}")

    def _name(self, path: str) -> str:
        return f"projects/{PROJECT}/databases/(default)/documents/{path}"

    # ------------------------------------------------------------------ messages (plain JSON)
    @staticmethod
    def _decode(msg: dict) -> dict:
        ct = (msg.get("ct") or "").lstrip()
        if not ct.startswith("{"):
            raise BotError("старое зашифрованное сообщение (или секретный чат): бот его не читает")
        return json.loads(ct)

    # ------------------------------------------------------------------ public API
    def chats(self) -> List[dict]:
        return self._query("", {
            "from": [{"collectionId": "chats"}],
            "where": {"fieldFilter": {"field": {"fieldPath": "members"}, "op": "ARRAY_CONTAINS", "value": _enc(self.uid)}},
        })

    def _chat(self, chat_id: str) -> dict:
        d = self._get(f"chats/{chat_id}")
        if not d:
            raise BotError("Чат не найден")
        return d

    def _send_content(self, chat_id: str, content: dict) -> None:
        chat = self._chat(chat_id)
        if chat.get("secret"):
            raise BotError("Боты не работают в секретных чатах")
        ct = json.dumps(content, ensure_ascii=False)
        now = int(time.time() * 1000)
        msg_id = uuid.uuid4().hex[:20]
        self._commit([
            {"update": {"name": self._name(f"chats/{chat_id}/messages/{msg_id}"), "fields": {
                "senderId": _enc(self.uid), "ct": _enc(ct), "a": _enc(""), "b": _enc(""),
                "ts": _enc(now), "readBy": _enc([self.uid]), "deleted": _enc(False)}},
             "currentDocument": {"exists": False}},
            {"update": {"name": self._name(f"chats/{chat_id}"), "fields": {
                "lastEnv": _enc({"ct": ct, "a": "", "b": ""}), "lastMsgId": _enc(msg_id),
                "lastSenderId": _enc(self.uid), "lastAt": _enc(now), "readers": _enc([self.uid])}},
             "updateMask": {"fieldPaths": ["lastEnv", "lastMsgId", "lastSenderId", "lastAt", "readers"]}},
        ])
        self._notify(chat_id, msg_id)

    def _notify(self, chat_id: str, msg_id: str) -> None:
        """Asks the push relay to send the notification to the other members (best effort, never raises)."""
        try:
            self._s.post(WORKER_URL.rstrip("/") + "/message", json={"chatId": chat_id, "msgId": msg_id},
                         headers={"Authorization": "Bearer " + self._token()}, timeout=10)
        except Exception:
            pass

    def send(self, chat_id: str, text: str, reply_to: str = "") -> None:
        self._send_content(chat_id, {"k": "text", "t": text, "p": "", "e": "", "w": 0, "h": 0, "m": "", "r": reply_to})

    def send_image(self, chat_id: str, data: bytes, width: int = 0, height: int = 0, sticker: bool = False) -> None:
        """Encrypts a picture (JPEG/PNG/WebP, < ~600 KB) and sends it."""
        if len(data) > 600_000:
            raise BotError("Картинка слишком большая (максимум ~600 КБ)")
        key = os.urandom(32)
        mid = str(uuid.uuid4())
        self._commit([{"update": {"name": self._name(f"media/{mid}"), "fields": {
            "chatId": _enc(chat_id), "by": _enc(self.uid), "ts": _enc(int(time.time() * 1000)), "ct": _enc(seal(key, data))}},
            "currentDocument": {"exists": False}}])
        self._send_content(chat_id, {"k": "sticker" if sticker else "image", "t": "", "p": "m:" + mid, "e": b64(key), "w": width, "h": height, "m": "", "r": ""})

    def download_image(self, ctx: Context) -> bytes:
        c = ctx.raw
        d = self._get("media/" + c["p"].removeprefix("m:"))
        if not d:
            raise BotError("Файл не найден")
        return open_(unb64(c["e"]), d["ct"])

    # ------------------------------------------------------------------ main loop
    def _poll_chat(self, chat: dict) -> None:
        cid = chat["_id"]
        last = self._seen.setdefault(cid, self._start)
        msgs = self._query(f"chats/{cid}", {
            "from": [{"collectionId": "messages"}],
            "where": {"fieldFilter": {"field": {"fieldPath": "ts"}, "op": "GREATER_THAN", "value": _enc(last)}},
            "orderBy": [{"field": {"fieldPath": "ts"}, "direction": "ASCENDING"}],
            "limit": 50,
        })
        for m in msgs:
            self._seen[cid] = max(self._seen[cid], m["ts"])
            if m.get("senderId") == self.uid or m.get("deleted"):
                continue
            try:
                c = self._decode(m)
            except Exception as e:                                   # pragma: no cover
                print(f"[{cid}] сообщение пропущено: {e}")
                continue
            ctx = Context(self, chat, m["senderId"], c.get("t", ""), c.get("k", "text"), c, m.get("_id", ""))
            try:
                fn = self._commands.get(ctx.command or "") if ctx.kind == "text" else None
                if fn:
                    fn(ctx)
                else:
                    for h in self._handlers:
                        h(ctx)
            except Exception as e:
                print("Ошибка в обработчике:", repr(e))

    def run(self) -> None:
        self._login()
        print(f"Бот @{self.username} запущен (uid {self.uid}). Ctrl+C - остановить.")
        while True:
            try:
                for chat in self.chats():
                    self._poll_chat(chat)
            except KeyboardInterrupt:
                raise
            except Exception as e:
                print("Ошибка опроса:", repr(e))
                time.sleep(5)
            time.sleep(self.poll)
