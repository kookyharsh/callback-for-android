# Recall Callback

Call dropped? Tap **Recall** to call back without opening your call history.

Recall Callback is an Android app that shows a floating redial button after a phone call ends. The button displays the caller’s name when available and closes after a 10-second countdown. Drag it to move it, or drop it on **Dismiss** at the bottom of the screen.

## Set up the app

1. Install the app on an Android 11 (API 30) or newer phone that can place calls.
2. Open **Recall Callback** and leave **Show Recall Button** and **Run in Background** enabled.
3. Allow phone and call log access when prompted. On Android 13 and newer, the app also requests notification permission for its foreground service.
4. In Android settings, allow the app to **Display over other apps**. On some phones this setting is called **Appear on top**.
5. After a call ends, tap **Recall** to redial the number.

The button is hidden during Do Not Disturb by default. Turn on **Show in DND Mode** in the app to show it during Do Not Disturb.

## Build from source

The project uses Kotlin and the Gradle wrapper. Install the Android SDK Platform for API 36.1 and use JDK 21 for Gradle.

```sh
gh repo clone kookyharsh/callback-for-android
cd callback-for-android
```

Open the project in Android Studio, sync Gradle, and run the `app` configuration on your phone. You can also build a debug APK from the repository root.

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS or Linux, run `./gradlew :app:assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Feedback

Found a bug or have an idea? [Open a GitHub issue](https://github.com/kookyharsh/callback-for-android/issues/new). For bugs, include your phone model, Android version, and steps to reproduce.
