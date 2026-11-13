# VPN Tethering Root

Application Android (Kotlin + Jetpack Compose) qui route le trafic d’un Hotspot Wi‑Fi vers une interface VPN (`tun0`) via **root** et **iptables** (`libsu`).

## Prérequis

- Appareil Android rooté (Magisk / KernelSU, etc.)
- Hotspot Wi‑Fi activé
- Client VPN connecté (interface `tun0`)

## Build local

```bash
./gradlew assembleDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`

## CI

Le workflow GitHub Actions `Build APK` compile l’APK debug et le publie en artefact téléchargeable.
