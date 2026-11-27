// Push notifications for blisschat. Requires the Blaze plan:  firebase deploy --only functions
// Pushes carry NO message text (messages are end-to-end encrypted): only "who" and "where".
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
admin.initializeApp();
const db = admin.firestore();

async function tokensOf(uid) {
  const snap = await db.collection("users").doc(uid).get();
  return { tokens: (snap.get("fcmTokens") || []).slice(-8), user: snap.data() || {} };
}

async function push(uid, data, android) {
  const { tokens } = await tokensOf(uid);
  if (!tokens.length) return;
  const res = await admin.messaging().sendEachForMulticast({ tokens, data, android });
  // drop tokens that are no longer valid
  const dead = [];
  res.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") dead.push(tokens[i]);
  });
  if (dead.length) await db.collection("users").doc(uid).update({ fcmTokens: admin.firestore.FieldValue.arrayRemove(...dead) });
}

exports.onCallCreated = onDocumentCreated("calls/{callId}", async (event) => {
  const call = event.data.data();
  if (!call || call.status !== "ringing") return;
  const caller = (await db.collection("users").doc(call.callerId).get()).data() || {};
  await push(call.calleeId, {
    type: "call", callId: event.params.callId, callerId: call.callerId,
    callerName: caller.displayName || caller.username || "blisschat", video: String(!!call.video),
  }, { priority: "high", ttl: 45000 });
});

exports.onCallChanged = onDocumentUpdated("calls/{callId}", async (event) => {
  const before = event.data.before.data(), after = event.data.after.data();
  if (before.status === "ringing" && after.status !== "ringing") {
    await push(after.calleeId, { type: "call_end", callId: event.params.callId }, { priority: "high", ttl: 30 });
  }
});

exports.onMessageCreated = onDocumentCreated("chats/{chatId}/messages/{msgId}", async (event) => {
  const msg = event.data.data();
  const chat = (await db.collection("chats").doc(event.params.chatId).get()).data();
  if (!msg || !chat) return;
  const sender = (await db.collection("users").doc(msg.senderId).get()).data() || {};
  const name = chat.type === "DIRECT" ? (sender.displayName || sender.username) : (chat.name || "blisschat");
  await Promise.all((chat.members || []).filter((m) => m !== msg.senderId).map((uid) =>
    push(uid, { type: "message", chatId: event.params.chatId, senderId: msg.senderId, senderName: name || "blisschat" }, { priority: "normal" })));
});
