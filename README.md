# MiuiBootShutdownLauncher

MVP demo app to open MIUI/Android system settings pages from app buttons.

## Tech Stack

- Vue 3 + Vite + TypeScript
- Capacitor Android
- Kotlin Capacitor plugin
- GitHub Actions for build and release

## Intent Strategy

### Scheduled power on/off page

1. Action: `miui.powercenter.intent.action.BOOT_SHUTDOWN_ONTIME`
2. Component: `com.miui.securitycenter/com.miui.powercenter.bootshutdown.PowerShutdownOnTime`
3. Fallback action: `miui.intent.action.POWER_MANAGER`

### Wireless debugging page

1. Component: `com.android.settings/.SubSettings`
2. Extra `:settings:show_fragment`: `com.android.settings.development.WirelessDebuggingFragment`
3. Fallback action: `android.settings.APPLICATION_DEVELOPMENT_SETTINGS`

## Frontend API

```ts
openBootShutdownPage(): Promise<{
  ok: boolean;
  method: "action" | "component" | "fallback" | "none";
}>

openWirelessDebuggingPage(): Promise<{
  ok: boolean;
  method: "wireless_debugging_fragment" | "developer_options" | "none";
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
