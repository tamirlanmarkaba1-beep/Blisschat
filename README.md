# blisschat v2 (Android · Kotlin · Jetpack Compose · Material 3)

End-to-end encrypted messenger: text, photos and P2P audio/video calls. Organic "blob" UI, a rainbow accent colour, email/username + Google login.

## Build the APK
**Cloud (no PC needed):** push the project to GitHub → *Actions → Build APK* → download `blisschat-apk` (`.github/workflows/build.yml`, Gradle 8.9 + JDK 17).
**Android Studio (Ladybug+):** open the folder → sync → *Build > Build APK(s)* → `app/build/outputs/apk/debug/app-debug.apk`.
Needs Gradle ≥ 8.9 and JDK 17 (old system Gradle in Termux will fail).
Nunito is downloaded at build time (falls back to the system font).

## Firebase setup (project `bitrate-71684`)
Firebase is initialised from code (`data/FirebaseConfig.kt`); no `google-services.json` is needed.
1. **Authentication → Sign-in method:** enable **Email/Password** and **Google**.
2. **Firestore** and **Storage:** create, then deploy `firestore.rules` and `storage.rules` (set `adminEmail()` in `firestore.rules` to the e-mail that owns `@tomatik_dev`).
3. **Google Sign-In:** add an Android app (package `com.bliss.blisschat`) in project settings with the SHA-1 of `app/blisschat.keystore`
   (`keytool -list -v -keystore app/blisschat.keystore -alias blisschat -storepass blisschat`), then copy the **Web client ID** (Authentication → Google → Web SDK configuration) into `FirebaseConfig.WEB_CLIENT_ID`. Without it the Google button shows an explanatory message; e-mail login works regardless.
4. **Calls across mobile networks / strict NAT** need a TURN server: add it to `FirebaseConfig.ICE_SERVERS`. Only Google STUN is configured.

## How the encryption works
- Each device creates an ECDH P-256 key pair; the private key is wrapped by an Android Keystore key. Public keys are published (`users/{uid}/keys/{kid}`, kid = hash of the key, so the server cannot swap it).
- DM: AES-256-GCM key from ECDH + HKDF. Groups: a random group key wrapped for every member. Photos: random per-file AES key, only ciphertext goes to Storage; the key travels inside the encrypted message.
- Calls: WebRTC (DTLS-SRTP) media; SDP and ICE are encrypted with the chat key, so Firestore cannot tamper with fingerprints.

## Features
Chats/groups with ✓/✓✓ checkmarks, long-press menus (own: delete for everyone / for me; others: delete for me + emoji reactions), call buttons in the header and in-call controls (mute, speaker, camera, flip, end), verified and "blisschat Support" badges, Admin panel (Settings, only for `tomatik_dev`), rainbow colour slider (Settings) controlling accent colours and background glow.

## Known limitations
- **Not compiled in the authoring sandbox** — fix any import/version nits Gradle reports (dependency versions to double-check: `stream-webrtc-android:1.1.1`, `googleid:1.1.1`, `credentials:1.3.0`).
- New device/reinstall = new key: old history on that device is unreadable.
- No push: incoming calls only ring while the app is open.
- Group members are not cryptographically signed (a member could impersonate another member in the group).
- Reactions, read receipts, profile data and avatars are not encrypted. Only photos are supported as media.
- `usernames/{name}` is publicly `get`-able (needed for login by username) and exposes the e-mail.
