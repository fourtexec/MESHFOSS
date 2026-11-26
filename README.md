# MESHFOSS — Liquid Glass Desktop (Windows / Linux / macOS) & Android Client for МЭШ

Free (GPLv3) client for **МЭШ** (`school.mos.ru`): schedule with live countdown, weighted grades & target GPA calculator, homework with native **ЦДЗ** solver, class ranking, attendance & turnstile passes, cafeteria meals & balance, digital **Москвёнок** QR pass, multi-account support, 11 themes, and 5 languages (`ru / en / es / fr / hu`).

## Authentic iOS 26 Liquid Glass

- **Android (`app/`)**: Upgraded `MESHFOSS` to integrate [`Kyant0/AndroidLiquidGlass`](https://github.com/Kyant0/AndroidLiquidGlass) (`io.github.kyant0:backdrop:2.0.1` & `io.github.kyant0:shapes:1.0.0`), using real live `LayerBackdrop`, AGSL rounded-rect SDF refraction, 7-band spectral chromatic dispersion, Apple vibrancy, directional/ambient specular highlights, and `Design.LIQUID` enabled by default.
- **Desktop & Web (`src/` + `desktop/`)**: Implements the exact RoundedRect SDF + `circleMap` optical refraction math from `Kyant0/AndroidLiquidGlass` and dynamic `<feDisplacementMap>` RGB chromatic dispersion from [`rdev/liquid-glass-react`](https://github.com/rdev/liquid-glass-react), paired with an interactive floating iOS 26 optical lens dock and real-time optical tuning sliders in Settings.

## Desktop App (Windows, Linux, macOS)

Native Electron shell in `desktop/` with:
- **macOS**: Tahoe `under-window` vibrancy + `hiddenInset` titlebar + `.dmg` & `.zip` (`arm64` + `x64`)
- **Windows**: Windows 11 Acrylic backdrop + NSIS installer & Portable `.exe` (`x64` + `arm64`)
- **Linux**: Translucent compositor + `.AppImage`, `.deb`, and `.rpm` (`x64` + `arm64`)
- CORS-free МЭШ HTTP proxy in Electron main process, OS-encrypted session storage (`safeStorage`), native `mos_cookies.json` file import, and system tray.

### Local Commands

```bash
# Run web preview / dev server (http://localhost:3000)
npm install
npm run dev

# Run core & optical refraction unit tests
npm test

# Build & package Desktop app for current OS
npm run desktop:build

# Build Android APK (JDK 17 + Android SDK 37.1)
./gradlew assembleRelease
```

## GitHub Actions CI/CD

- `.github/workflows/desktop.yml` — Matrix build for **Windows**, **Linux**, and **macOS** installers, publishing to GitHub Releases on `v*` tags and rolling `desktop-nightly` prereleases on `main`.
- `.github/workflows/android.yml` — Runs Kotlin JVM tests and builds signed Android release APKs.

## License

GPLv3 — see `LICENSE`.
