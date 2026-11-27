// blisschat push relay - Cloudflare Worker (free plan, no credit card needed).
//
// Why it exists: Firebase Cloud Functions need the paid Blaze plan. This worker does the same job for free.
// The app calls it right after a message is sent / a call is started:
//
//   POST /message  { chatId, msgId }   Authorization: Bearer <Firebase ID token of the sender>
//   POST /call     { callId }          Authorization: Bearer <Firebase ID token of caller or callee>
//
// Security: the caller's own ID token is used to read the chat / message / call from Firestore, so Firestore's
// security rules decide whether the caller is allowed to see them. Only then is the push sent, with the service
// account stored in the SERVICE_ACCOUNT_JSON secret (never in the code, never in the app).
// Pushes carry NO message text (messages are end-to-end encrypted): only "who" and "where".

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const DATASTORE_SCOPE = "https://www.googleapis.com/auth/datastore";

/* ------------------------------------------------------------------ helpers */

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });

function b64url(input) {
  const bytes = input instanceof ArrayBuffer ? new Uint8Array(input) : input;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
const textB64url = (str) => b64url(new TextEncoder().encode(str));

/** The uid inside a Firebase ID token. Not verified here: Firestore verifies the token on the first request. */
function uidOf(idToken) {
  try {
    const payload = JSON.parse(atob(idToken.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return payload.user_id || payload.sub || null;
  } catch {
    return null;
  }
}

/* ------------------------------------------------------------------ service account -> OAuth access token */

let cachedToken = { value: null, exp: 0 };

async function adminToken(env) {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken.value && cachedToken.exp - 60 > now) return cachedToken.value;

  const sa = JSON.parse(env.SERVICE_ACCOUNT_JSON);
  const aud = sa.token_uri || "https://oauth2.googleapis.com/token";
  const header = { alg: "RS256", typ: "JWT" };
  const claim = { iss: sa.client_email, scope: `${FCM_SCOPE} ${DATASTORE_SCOPE}`, aud, iat: now, exp: now + 3600 };
  const unsigned = textB64url(JSON.stringify(header)) + "." + textB64url(JSON.stringify(claim));

  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));

  const res = await fetch(aud, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: unsigned + "." + b64url(sig),
    }),
  });
  if (!res.ok) throw new Error("token exchange failed: " + res.status + " " + (await res.text()));
  const j = await res.json();
  cachedToken = { value: j.access_token, exp: now + (j.expires_in || 3600) };
  return cachedToken.value;
}

/* ------------------------------------------------------------------ Firestore REST */

const docsUrl = (env) =>
  `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents`;

async function fsGet(env, path, bearer) {
  const r = await fetch(`${docsUrl(env)}/${path}`, { headers: { authorization: "Bearer " + bearer } });
  return r.ok ? r.json() : null;
}

const str = (field) => (field && field.stringValue) || "";
const strings = (field) =>
  ((field && field.arrayValue && field.arrayValue.values) || []).map((v) => v.stringValue).filter(Boolean);

/* ------------------------------------------------------------------ FCM */

async function removeDeadTokens(env, admin, uid, dead) {
  await fetch(`${docsUrl(env)}:commit`, {
    method: "POST",
    headers: { authorization: "Bearer " + admin, "content-type": "application/json" },
    body: JSON.stringify({
      writes: [{
        transform: {
          document: `projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${uid}`,
          fieldTransforms: [{ fieldPath: "fcmTokens", removeAllFromArray: { values: dead.map((t) => ({ stringValue: t })) } }],
        },
      }],
    }),
  });
}

/** Sends a data push to every token of one user and forgets tokens that FCM says no longer exist. */
async function pushToUser(env, admin, uid, data, android) {
  const user = await fsGet(env, `users/${uid}`, admin);
  if (!user) return;
  const tokens = strings(user.fields && user.fields.fcmTokens).slice(-8);
  const dead = [];
  await Promise.all(tokens.map(async (token) => {
    const r = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`, {
      method: "POST",
      headers: { authorization: "Bearer " + admin, "content-type": "application/json" },
      body: JSON.stringify({ message: { token, data, android } }),
    });
    if (!r.ok) {
      const body = await r.text();
      if (r.status === 404 || body.includes("UNREGISTERED")) dead.push(token);
      else console.log("fcm error", r.status, body.slice(0, 200));
    }
  }));
  if (dead.length) await removeDeadTokens(env, admin, uid, dead);
}

/* ------------------------------------------------------------------ handlers */

async function handleMessage(env, idToken, uid, body) {
  const { chatId, msgId } = body || {};
  if (!chatId || !msgId) return json({ error: "chatId and msgId required" }, 400);

  // Read with the CALLER's token: Firestore rules prove he is a member of the chat and may see the message.
  const chat = await fsGet(env, `chats/${chatId}`, idToken);
  if (!chat) return json({ error: "forbidden" }, 403);
  const members = strings(chat.fields.members);
  if (!members.includes(uid)) return json({ error: "forbidden" }, 403);

  const msg = await fsGet(env, `chats/${chatId}/messages/${msgId}`, idToken);
  if (!msg || str(msg.fields.senderId) !== uid) return json({ error: "not your message" }, 403);
  const ts = Number((msg.fields.ts && msg.fields.ts.integerValue) || 0);
  if (Math.abs(Date.now() - ts) > 5 * 60 * 1000) return json({ error: "message too old" }, 400);

  const admin = await adminToken(env);
  const sender = await fsGet(env, `users/${uid}`, admin);
  const sf = (sender && sender.fields) || {};
  const direct = str(chat.fields.type) === "DIRECT";
  const name = direct ? (str(sf.displayName) || str(sf.username) || "blisschat") : (str(chat.fields.name) || "blisschat");

  // Ordinary chats are stored as plain JSON, so the notification can show the text. Secret chats stay hidden.
  let preview = "Новое сообщение";
  const ct = str(msg.fields.ct).trim();
  if (!(chat.fields.secret && chat.fields.secret.booleanValue) && ct.startsWith("{")) {
    try {
      const c = JSON.parse(ct);
      const labels = { image: "📷 Фото", sticker: "🙂 Стикер", voice: "🎤 Голосовое сообщение", poll: "📊 Опрос", file: "📎 Файл", call: "📞 Звонок" };
      preview = labels[c.k] || String(c.t || "Новое сообщение");
      if (!direct) preview = ((str(sf.displayName) || str(sf.username)) + ": ").replace(/^: $/, "") + preview;
      preview = preview.slice(0, 160);
    } catch (_) { /* keep the default text */ }
  }

  const data = { type: "message", chatId, senderId: uid, senderName: name, body: preview };
  await Promise.all(
    members.filter((m) => m !== uid).map((m) => pushToUser(env, admin, m, data, { priority: "HIGH" }))
  );
  return json({ ok: true });
}

/** POST /test: sends a test push to the caller's OWN phones (used by "Проверить уведомления" in the app). */
async function handleTest(env, uid) {
  const admin = await adminToken(env);
  const user = await fsGet(env, `users/${uid}`, admin);
  const tokens = strings(user && user.fields && user.fields.fcmTokens);
  if (!tokens.length) return json({ ok: false, error: "no tokens", tokens: 0 });
  await pushToUser(env, admin, uid, { type: "message", chatId: "test", senderId: "test", senderName: "blisschat", body: "Тестовое уведомление: всё работает 🎉" }, { priority: "HIGH" });
  return json({ ok: true, tokens: tokens.length });
}

async function handleCall(env, idToken, uid, body) {
  const { callId } = body || {};
  if (!callId) return json({ error: "callId required" }, 400);

  const call = await fsGet(env, `calls/${callId}`, idToken); // rules: only caller / callee can read
  if (!call) return json({ error: "forbidden" }, 403);
  const f = call.fields;
  const callerId = str(f.callerId);
  const calleeId = str(f.calleeId);
  if (uid !== callerId && uid !== calleeId) return json({ error: "forbidden" }, 403);

  const admin = await adminToken(env);
  if (str(f.status) === "ringing") {
    if (uid !== callerId) return json({ ok: true }); // only the caller starts a ring
    const caller = await fsGet(env, `users/${callerId}`, admin);
    const cf = (caller && caller.fields) || {};
    await pushToUser(env, admin, calleeId, {
      type: "call", callId, callerId,
      callerName: str(cf.displayName) || str(cf.username) || "blisschat",
      video: String(!!(f.video && f.video.booleanValue)),
    }, { priority: "HIGH", ttl: "45s" });
  } else {
    // cancelled / ended / declined: stop the ringing on the callee's phone
    await pushToUser(env, admin, calleeId, { type: "call_end", callId }, { priority: "HIGH", ttl: "30s" });
  }
  return json({ ok: true });
}

/* ------------------------------------------------------------------ entry point */

export default {
  async fetch(request, env) {
    if (request.method !== "POST") return new Response("blisschat push relay is running", { status: 200 });

    const auth = request.headers.get("authorization") || "";
    const idToken = auth.startsWith("Bearer ") ? auth.slice(7) : "";
    const uid = uidOf(idToken);
    if (!uid) return json({ error: "missing token" }, 401);

    let body;
    try { body = await request.json(); } catch { return json({ error: "bad json" }, 400); }

    try {
      const path = new URL(request.url).pathname;
      if (path === "/message") return await handleMessage(env, idToken, uid, body);
      if (path === "/call") return await handleCall(env, idToken, uid, body);
      if (path === "/test") return await handleTest(env, uid);
      return json({ error: "not found" }, 404);
    } catch (e) {
      console.log("worker error", String(e));
      return json({ error: "server error" }, 500);
    }
  },
};
