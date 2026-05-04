# MiuiBootShutdownLauncher

MVP Demo: click one button and open MIUI "定时开关机" page via Android Intent.

## Stack

- Vue 3 + Vite + TypeScript
- Capacitor Android
- Kotlin Capacitor Plugin
- GitHub Actions release APK build

## Open Strategy

1. Action: `miui.powercenter.intent.action.BOOT_SHUTDOWN_ONTIME`
2. Explicit component: `com.miui.securitycenter/com.miui.powercenter.bootshutdown.PowerShutdownOnTime`
3. Fallback action: `miui.intent.action.POWER_MANAGER`

## Frontend API

```ts
openBootShutdownPage(): Promise<{
  ok: boolean;
  method: "action" | "component" | "fallback" | "none";
}>
```

## Release Signing (Reusable Key)

Generate one keystore and reuse it forever for upgrades:

```powershell
keytool -genkeypair `
  -alias miui-power-release `
  -keyalg RSA `
  -keysize 2048 `
  -validity 36500 `
  -storetype JKS `
  -keystore .\release-keystore.jks
```

Convert to base64 for GitHub Secrets:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\release-keystore.jks")) | Set-Content .\release-keystore.base64.txt
```

Configure repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

The workflow decodes the keystore and runs `assembleRelease` with signing env vars.

## CI

Workflow file: `.github/workflows/android-release.yml`

Triggers:

- push to `main`
- manual dispatch
