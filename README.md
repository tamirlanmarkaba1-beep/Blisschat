# blisschat (Android, Kotlin + Jetpack Compose + Material 3)

## Build the APK
1. Unzip, then in Android Studio (Ladybug 2024.2 or newer): **File > Open** the `blisschat` folder.
2. Let Gradle sync (it downloads Gradle 8.9 from `gradle/wrapper/gradle-wrapper.properties`, needs internet). If asked for JDK, use the bundled JDK 17+.
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The file is at `app/build/outputs/apk/debug/app-debug.apk`.
   Or in a terminal: `gradle assembleDebug` (or `./gradlew assembleDebug` after Android Studio creates the wrapper).
4. Copy the APK to your phone and install it (allow "install unknown apps").

The Nunito font is downloaded during the build. To work offline, put `Nunito.ttf` (variable font) at `app/src/main/assets/fonts/Nunito.ttf`. If neither works the app uses the system font.

## Firebase setup (project `bitrate-71684`)
The app initialises Firebase from code (`data/FirebaseConfig.kt`) using your web config, so no `google-services.json` is required. If you prefer the standard way, register an Android app with package `com.bliss.blisschat`, add `google-services.json` to `app/`, and add the `com.google.gms.google-services` plugin.

In the Firebase console:
1. **Authentication > Sign-in method**: enable **Anonymous** (used by debug login).
2. **Firestore Database**: create it, then paste `firestore.test.rules` while testing.
3. **Storage**: create it, paste `storage.rules`.

## Login modes
- **Debug login (default)**: no mail server. The 6-digit code appears on the code screen. Switch in the login screen or Settings > Developer.
- **Real e-mail OTP**: Blaze plan required. In `functions/`: `npm install`, set secrets `SMTP_URL` and `MAIL_FROM` (see `functions/index.js`), run `firebase deploy --only functions,firestore:rules,storage`. Turn debug login off. Then switch to `firestore.rules` (set `adminEmail()` first).

## Admin
Username `tomatik_dev` (case-insensitive) gets Super Admin: Admin Panel in Settings (verified toggle, blisschat Support toggle, delete any channel) and a "Delete channel (admin)" item in any channel's menu.
**Security note:** usernames are first-come. Whoever registers `tomatik_dev` first becomes admin in the app. With the production rules, only the e-mail in `adminEmail()` can take that name. With the test rules anyone can.
