# Dialer

A phone app for Android with no account, no tracking and no ads.

Dialer is being built. This first version has the app's look and its
settings; calls, recent calls and contacts arrive in the next versions.

## Install

Download the latest APK from the
[Releases](https://github.com/213YaZ786/Dialer/releases) page and install
it. Android 12 or newer is required.

## Privacy

- Nothing you do in the app leaves your phone.
- The only connection is one request to GitHub when the app opens, to see
  whether a newer version is out. You can turn it off in Settings.

## For developers

Kotlin and Jetpack Compose, single module. Build with Android Studio or:

```
gradle :app:assembleDebug
```

CI builds on every push. For a signed release, add the repository secrets
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

MIT licensed. Icons from Google's Material Icons, Apache License 2.0.
