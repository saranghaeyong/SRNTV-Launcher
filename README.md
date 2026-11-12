# SRNTV Launcher

A premium, AMOLED-focused, liquid-glass Android TV launcher.

## Design Philosophy
SRNTV Launcher is built for those who prefer minimalism and high-end aesthetics. It rejects cluttered app grids, ads, and recommendations in favor of a clean, cinematic, "Black & Red" identity that feels modern and unobtrusive on a large TV screen.

## Features
- **Cinematic AMOLED Interface**: Pure black background (`#000000`) for true contrast on OLED/high-end TV panels.
- **Liquid Glass Design**: Elegant, translucent dark glass UI elements with subtle red highlights and neon-style red ambient glow.
- **Dynamic Wallpaper System**: Atmospheric wallpapers that cycle and crossfade upon app navigation.
- **Premium Typography**: Uses the classic `PriceDow` font for a bold, distinctive app-selection experience.
- **Minimalist Control**: A single, focused app title in the center, with a subtle "PRESS OK TO OPEN" hint.
- **Smart Quick-Settings**: Floating glass pill for quick access to Inputs, Wi-Fi, and System Settings, featuring focus-aware red glow animations.
- **Context-Aware**: Auto-updates clock, date, and greeting.
- **TV Optimized**: Full D-pad navigation support and Android TV Leanback compatibility.

## Tech Stack
- Kotlin
- Android Views / Data Binding
- Android TV Leanback / Launcher Intent standards

## Installation Instructions

### Prerequisites
- Android TV Device (or Emulator)
- ADB enabled on TV device
- ADB installed on your computer

### Installing the APK
1. Download the latest `SRNTV-Launcher.apk` from the [Releases page](https://github.com/saranghaeyong/SRNTV-Launcher/releases).
2. Install via ADB:
   ```bash
   adb install -r SRNTV-Launcher.apk
   ```
3. Set as default home:
   ```bash
   adb shell cmd package set-home-activity com.srntv.launcher/.LauncherActivity
   ```

## Setup Note (Assets)
For licensing reasons, the wallpaper images and the `PriceDow` font are not included in this source repository.
- **To add your own wallpapers:** Create a directory at `app/src/main/assets/wallpapers/` and add 4 PNG files named `1.png`, `2.png`, `3.png`, `4.png`.
- **To add the font:** Add your `pricedow.ttf` file to `app/src/main/res/font/pricedow.ttf`.

## Privacy
This launcher has **zero** ads, **zero** analytics, and **zero** background telemetry. It requires minimal permissions only to discover and launch apps.

## Credits
Built with ❤️ for a cleaner TV experience.
