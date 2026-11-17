/**
 * blisschat - e-mail OTP login.
 *
 *   sendOtp({email})        -> generates a 6-digit code, stores its hash, e-mails it
 *   verifyOtp({email,code}) -> checks the code, returns a Firebase custom token
 *
 * Needs the Blaze plan. Configure SMTP (any provider: Gmail app password, Brevo, Mailgun, ...):
 *
 *   firebase functions:secrets:set SMTP_URL
 *   # value example: smtps://user%40gmail.com:APP_PASSWORD@smtp.gmail.com:465
 *   firebase functions:secrets:set MAIL_FROM
 *   # value example: blisschat <user@gmail.com>
 *
 * Then: firebase deploy --only functions
 */
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");
const crypto = require("crypto");

admin.initializeApp();
const db = admin.firestore();

const SMTP_URL = defineSecret("SMTP_URL");
const MAIL_FROM = defineSecret("MAIL_FROM");

const CODE_TTL_MS = 10 * 60 * 1000;
const RESEND_MIN_MS = 30 * 1000;
const MAX_ATTEMPTS = 5;

const sha = (s) => crypto.createHash("sha256").update(s).digest("hex");
const normEmail = (e) => String(e || "").trim().toLowerCase();
const isEmail = (e) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e);

/** Must match AuthRepository.uidFor() in the Android app. */
const uidFor = (email) => sha(email).slice(0, 28);

exports.sendOtp = onCall({ secrets: [SMTP_URL, MAIL_FROM], region: "us-central1" }, async (req) => {
  const email = normEmail(req.data && req.data.email);
  if (!isEmail(email)) throw new HttpsError("invalid-argument", "Please enter a valid email address");

  const ref = db.collection("otp").doc(sha(email));
  const snap = await ref.get();
  const now = Date.now();
  if (snap.exists && now - snap.get("sentAt") < RESEND_MIN_MS) {
    throw new HttpsError("resource-exhausted", "Please wait a moment before requesting another code");
  }

  const code = String(crypto.randomInt(0, 1000000)).padStart(6, "0");
  await ref.set({ codeHash: sha(code + ":" + email), sentAt: now, expiresAt: now + CODE_TTL_MS, attempts: 0 });

  const transport = nodemailer.createTransport(SMTP_URL.value());
  await transport.sendMail({
    from: MAIL_FROM.value(),
    to: email,
    subject: `${code} is your blisschat code`,
    text: `Your blisschat verification code is ${code}. It expires in 10 minutes.\n\nIf you didn't request it, you can ignore this email.`,
    html: `<div style="font-family:Arial,sans-serif;max-width:420px;margin:auto;padding:24px">
      <h2 style="margin:0 0 12px">blisschat</h2>
      <p>Your verification code is</p>
      <p style="font-size:34px;font-weight:700;letter-spacing:8px;margin:12px 0">${code}</p>
      <p style="color:#666">It expires in 10 minutes. If you didn't request it, ignore this email.</p></div>`,
  });
  return { ok: true };
});

exports.verifyOtp = onCall({ region: "us-central1" }, async (req) => {
  const email = normEmail(req.data && req.data.email);
  const code = String((req.data && req.data.code) || "").trim();
  if (!isEmail(email) || !/^\d{6}$/.test(code)) throw new HttpsError("invalid-argument", "Invalid code");

  const ref = db.collection("otp").doc(sha(email));
  const result = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return "missing";
    const d = snap.data();
    if (Date.now() > d.expiresAt) { tx.delete(ref); return "expired"; }
    if (d.attempts >= MAX_ATTEMPTS) { tx.delete(ref); return "locked"; }
    if (d.codeHash !== sha(code + ":" + email)) { tx.update(ref, { attempts: d.attempts + 1 }); return "wrong"; }
    tx.delete(ref);
    return "ok";
  });

  if (result === "missing" || result === "expired") throw new HttpsError("failed-precondition", "That code has expired. Request a new one");
  if (result === "locked") throw new HttpsError("resource-exhausted", "Too many attempts. Request a new code");
  if (result === "wrong") throw new HttpsError("permission-denied", "That code is not correct");

  const uid = uidFor(email);
  // `email` claim is what firestore.rules uses to protect the Super Admin username.
  const token = await admin.auth().createCustomToken(uid, { email });
  return { token };
});
