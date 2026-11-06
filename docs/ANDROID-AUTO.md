# Android Auto

Musonic exposes its music library through Media3 MediaLibraryService. It uses the server account already connected on the phone. Albums, favorites, recent songs and playlists are available to car controllers, with search and playback queue controls.

## Testing a sideloaded APK

1. Install Musonic and connect a server on the phone.
2. Enable Android Auto developer mode by tapping its version information ten times.
3. Enable **Unknown sources** in Android Auto developer settings.
4. Reconnect and look for Musonic under **Customize launcher**.

Android Auto's developer option is separate from Android's permission to install APKs. See [Google's media-app testing guide](https://developer.android.com/training/cars/testing#unknown-sources) for the current requirements.

This integration still needs validation on physical head units. Automated tests cover modern and legacy browser connections, browsing, search, playback queues and account changes. They do not establish vehicle compatibility or Google Play approval.
