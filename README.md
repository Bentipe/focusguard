# Focus Guard

A small, deliberately stubborn Android app that keeps social media, short-video, YouTube and online-shopping apps and websites off your phone, and protects itself from being switched off on impulse.

Built for a Pixel 8 on Android 17, plain Java, no dependencies, no network access, no data collection.

## What it blocks

| Area | How |
|---|---|
| **Apps** (~75: TikTok, YouTube, YouTube Music, Google TV, Instagram, Facebook, Snapchat, X, Reddit, Pinterest, Threads, LinkedIn, Discord, Twitch, Temu, Shein, Amazon, AliExpress, Wallapop, Vinted, Zalando, Just Eat, Uber Eats, Glovo, …) | Opening one sends you straight to the home screen. Installing one pops up the uninstall screen for it. |
| **Websites** (~100 social, video and shopping domains) | Reads the address bar in Chrome and Firefox and backs out of blocked pages. |
| **Play Store** | Backs out of the store page of a blocked app, so it can't be installed or re-enabled. |
| **Other browsers** | Any app that can open arbitrary web links, other than Chrome and Firefox, is treated as blocked, so installing another browser doesn't get around it. |
| **Disabled system apps** | Blocks the "Enable" button for YouTube, YouTube Music and Google TV in Settings. |
| **Itself** | Backs out of its own Uninstall / Force stop / Clear storage / Deactivate admin / accessibility toggle screens. There is no off switch in the app. |

**Allowed on purpose:** Steam, WeChat, Strava, Movistar Plus+.

The full lists are in [`Blocklist.java`](src/app/focusguard/Blocklist.java). Edit and rebuild to change them.

## How it works

- **Accessibility service** (`GuardService`). It watches which app is in the foreground and reads the text of the Play Store, Settings and browser windows, then acts with the Home and Back buttons. Nothing is logged or sent anywhere.
- **Device admin** (`AdminReceiver`). An app that is an active device admin can't be uninstalled until it's deactivated, and the deactivate screen is one of the screens it blocks.
- **Device owner (optional, stronger)**. If the app is made device owner, it also uninstalls blocked apps silently, hides them, blocks its own uninstall at system level, and disables safe-mode boot. Android only allows this on a phone with **no accounts**, so it's not set up by default.
- The APK is built `testOnly`. That's what allows a computer to remove the device admin over adb (see below), and it's the only way out.

## Build

You need a JDK (8+) and an Android SDK with `build-tools/36.0.0` and `platforms/android-36`. Gradle isn't needed.

```bash
./build.sh            # -> build/focusguard.apk
```

The script creates a local `focusguard.keystore` on the first build. It's git-ignored. Keep it if you want to install updates over an existing install, because a new key means uninstalling first.

## Install & activate

1. On the phone, turn on **Developer options → USB debugging** and connect it.
2. Install it. `-t` is required because the APK is `testOnly`:
   ```bash
   adb install -t -r build/focusguard.apk
   ```
3. Open **Focus Guard** on the phone, in this order:
   1. **Step 1 · Protect against uninstall** → *Activate*.
   2. **Step 2 · Turn on blocking** → Accessibility → *Focus Guard* → switch on.
      If the switch is greyed out ("restricted setting"), allow it with:
      ```bash
      adb shell appops set app.focusguard ACCESS_RESTRICTED_SETTINGS allow
      ```
4. The app shows **✅ Active**.

### Optional: device owner mode

Only possible when the phone has no accounts on it: remove every Google and app account, or start from a fresh factory reset.

```bash
adb shell dpm set-device-owner app.focusguard/.AdminReceiver
```

Add your accounts back afterwards. In this mode, safe mode is disabled and blocked apps are removed silently.

## Uninstall

This is meant to need a computer. From the phone itself, the uninstall and deactivate screens are blocked.

1. Connect the phone with USB debugging on.
2. Remove the device admin. This works only because the APK is `testOnly`:
   ```bash
   adb shell dpm remove-active-admin app.focusguard/.AdminReceiver
   ```
3. Uninstall:
   ```bash
   adb uninstall app.focusguard
   ```

If it was made **device owner**, step 2 is instead:

```bash
adb shell dpm remove-active-admin app.focusguard/.AdminReceiver   # also clears device owner for testOnly apps
```

If that fails, the only other way is a factory reset.

**Re-enabling the preinstalled apps** that were disabled (YouTube, YouTube Music, Google TV), once Focus Guard is gone:

```bash
adb shell pm enable com.google.android.youtube
adb shell pm enable com.google.android.apps.youtube.music
adb shell pm enable com.google.android.videos
```

## Known gaps

- **Safe mode** turns off third-party accessibility services. From there the admin could be deactivated by hand. Only device owner mode closes this.
- **In-app browsers** (for example links opened inside the Google app or a chat app's built-in viewer) aren't filtered, only Chrome and Firefox are.
- **Screen-text heuristics**: the Settings and Play Store checks match on-screen words in Spanish and English. A different phone language or a big UI update may need new words in `GuardService.java`.
- **USB debugging**: anyone with a computer and the phone unlocked can remove it. That's the intended escape hatch.
