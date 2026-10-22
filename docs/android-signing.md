# Android signing and install identity

The app identity is `com.alvinb.counterpos`. Do not change the `applicationId` unless a deliberate package migration is planned; Android treats a changed application ID as a different app.

## Internal and emulator builds

The repository contains `debug.keystore`, and `app/build.gradle.kts` signs the `debug` variant with that file using the standard debug alias `androiddebugkey` and password `android`. This is intentional: every clone, AI-generated build, and emulator install built from this repository has the same debug certificate and can update the existing internal app instead of producing a signature-conflict error.

This keystore is **not a secret**. Anyone who can clone the repository can create an APK that appears to be an internal build of this package. Never use it for Google Play, customer distribution, or any production release.

Build and install the shared-identity debug APK with:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If the emulator already contains an APK signed by a different key, uninstall that old app once, then install the repository build. Future builds from this repository will update normally.

## Production release builds

The production upload/release keystore must remain outside Git. Configure these GitHub Actions secrets (or equivalent protected CI secrets):

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The release key must be backed up securely. Losing it prevents updates to the published application. Never commit `my-upload-key.jks`, `keystore.properties`, or release passwords.
