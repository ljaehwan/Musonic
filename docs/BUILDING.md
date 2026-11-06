# Build Musonic

Use JDK 17, Android SDK Platform 36 and Build Tools 35.0.0. The checked-in wrapper downloads Gradle 8.11.1 and verifies its SHA-256 checksum. Android Studio can install the SDK; point `ANDROID_HOME` at it or set `sdk.dir` in an untracked `local.properties` file.

## Development build

```sh
./gradlew testDebugUnitTest testPlayUnitTest lintDebug lintPlay assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` on Android 8.0 or newer. Gradle creates a local debug signing key automatically. No production account, server or private signing material is required to build or run the unit tests.

To run device tests, connect an emulator or test phone and run `./gradlew connectedDebugAndroidTest`. Tests use local mock servers and synthetic audio. Keep your personal library out of screenshots and test fixtures.

## Distribution variants

| Variant | Server transport | Update behavior |
| --- | --- | --- |
| debug / release | HTTPS, or HTTP after explicit opt-in | Direct APK update UI |
| play | HTTPS only | No APK updater or installation permission |

```sh
./gradlew bundlePlay
node tools/verify-play.cjs
```

Without signing environment variables, the Play bundle is unsigned. To sign your own build, supply `MUSONIC_UPLOAD_STORE`, `MUSONIC_UPLOAD_STORE_PASSWORD` and `MUSONIC_UPLOAD_KEY_PASSWORD`; the key alias is `upload`. Keep these outside the repository and shell history. The verification script needs Node.js and `unzip` and checks the Play manifest, updater exclusion and 64-bit native library alignment.

Override versions with `-PmusonicVersionCode=123` and `-PmusonicVersionName=0.1.0` as appropriate for your distribution. The defaults are development values, not the version of the latest published APK.

## Forks and signatures

The direct updater currently points at `ljaehwan/Musonic-releases`. For a separately distributed fork, change the update source and application ID before shipping. An official APK cannot update a local build signed with a different key. Do not copy another project's private signing key or disable signature verification to work around this.

The public workflow only validates and builds. Official signed release delivery is managed separately; no deployment credentials are required in a contributor fork.
