# MiuiBootShutdownLauncher

MVP demo app to open the MIUI scheduled power on/off page from one button.

## Tech Stack

- Vue 3 + Vite + TypeScript
- Capacitor Android
- Kotlin Capacitor plugin
- GitHub Actions for build and release

## Intent Strategy

1. Action: `miui.powercenter.intent.action.BOOT_SHUTDOWN_ONTIME`
2. Component: `com.miui.securitycenter/com.miui.powercenter.bootshutdown.PowerShutdownOnTime`
3. Fallback action: `miui.intent.action.POWER_MANAGER`

## Frontend API

```ts
openBootShutdownPage(): Promise<{
  ok: boolean;
  method: "action" | "component" | "fallback" | "none";
}>
```

## Signing Variables

Configure one reusable keystore and set these repository secrets or variables:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

## Workflows

### 1) Build workflow

File: `.github/workflows/android-release.yml`

- Trigger: push to `main` or manual dispatch
- Output: signed release APK artifact named `MiuiBootShutdownLauncher-vX.Y.Z.apk`

### 2) Publish workflow

File: `.github/workflows/publish-version.yml`

- Trigger: manual dispatch
- Behavior:
  - Calculates next tag in `v1.0.n` format
  - Bumps `android/app/build.gradle` (`versionCode`, `versionName`)
  - Bumps `package.json` + `package-lock.json` version
  - Builds signed release APK
  - Commits version bump to `main`
  - Creates tag `v1.0.n`
  - Creates GitHub Release and uploads direct APK asset
