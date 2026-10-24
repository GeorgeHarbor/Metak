# Metak

A lightweight Android TV launcher modeled on the Apple TV home screen.

- **Continue Watching hero:** the newest titles from **Nuvio** and **SmartTube**, with a full-screen backdrop, the episode name, time left and a progress bar. Select a card to resume right where you stopped.
- **App grid:** every TV app as a rounded banner that zooms on focus, tvOS style.
- **Tiny:** plain Java on the framework only, no libraries. The release APK is about 20 KB.

## How Continue Watching works

Nuvio and SmartTube publish what you are watching to Android TV's system **Watch Next** table. Metak reads that table and keeps only rows from those two apps. This needs the *TV listings* permission, which Metak asks for on first launch. To grant it over adb instead:

```sh
adb shell pm grant app.metak android.permission.READ_TV_LISTINGS
```

## Build and install

```sh
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

Then press Home and choose **Metak**. On Google TV, which hides the home picker, run:

```sh
adb shell cmd package set-home-activity app.metak/.HomeActivity
```

**Switching launchers:** with Metak as the default, pressing Home while Metak is already on screen opens your other launcher. Press Home again to come back. To go back to the stock launcher for good:

```sh
adb shell cmd package set-home-activity <stock.launcher.package>
```

Requires Android 8.0 (API 26) or newer.
