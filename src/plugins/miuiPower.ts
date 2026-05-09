import { registerPlugin } from "@capacitor/core";

export interface OpenBootShutdownResult {
  ok: boolean;
  method: "action" | "component" | "fallback" | "none";
}

export interface OpenWirelessDebuggingResult {
  ok: boolean;
  method: "wireless_debugging_fragment" | "developer_options" | "none";
}

export interface OpenDeveloperOptionsResult {
  ok: boolean;
  method: "application_development_settings" | "none";
}

export interface OpenScreenRefreshRateResult {
  ok: boolean;
  method:
    | "xiaomi_misettings_refresh_rate_activity"
    | "miui_refresh_rate_action"
    | "refresh_rate_fragment"
    | "display_settings"
    | "none";
}

export interface OpenHonorOfKingsResult {
  ok: boolean;
  method: "package_launch_intent" | "none";
  installed: boolean;
  packageName: string;
  error?: string;
}

export interface OpenAppCommandResult {
  ok: boolean;
  method: string;
  installed?: boolean;
  packageName?: string;
  error?: string;
  url?: string;
}

export type LaunchIntentExtraValue = string | number | boolean;

export interface LaunchIntentOptions {
  action?: string;
  packageName?: string;
  className?: string;
  dataUri?: string;
  mimeType?: string;
  categories?: string[];
  extras?: Record<string, LaunchIntentExtraValue>;
  chooser?: boolean;
}

export interface OverlayPermissionResult {
  granted: boolean;
}

export interface AccessibilityPermissionResult {
  granted: boolean;
}

export interface FocusOverlayResult {
  ok: boolean;
  method:
    | "focus_overlay_service"
    | "overlay_permission_required"
    | "manage_overlay_permission"
    | "accessibility_settings"
    | "none";
  permissionRequired?: boolean;
  overlayPermissionRequired?: boolean;
  accessibilityPermissionRequired?: boolean;
  accessibilityEnabled?: boolean;
  accessibilitySettingsOpened?: boolean;
  overlaySettingsOpened?: boolean;
  error?: string;
}

export interface WindowFocusInfoResult {
  ok: boolean;
  source?: string;
  command: string;
  lines: string[];
  raw: string;
  error: string;
  exitCode: number;
  timedOut: boolean;
  elapsedMs?: number;
  packageName?: string;
  className?: string;
  windowTitle?: string;
  texts?: string[];
  timestamp?: number;
  accessibilityEnabled?: boolean;
}

export interface MiuiPowerPlugin {
  openBootShutdownPage(): Promise<OpenBootShutdownResult>;
  openWirelessDebuggingPage(): Promise<OpenWirelessDebuggingResult>;
  openDeveloperOptions(): Promise<OpenDeveloperOptionsResult>;
  openScreenRefreshRatePage(): Promise<OpenScreenRefreshRateResult>;
  openHonorOfKings(): Promise<OpenHonorOfKingsResult>;
  openScreenTimePage(): Promise<OpenAppCommandResult>;
  openUsageAccessSettings(): Promise<OpenAppCommandResult>;
  openFileManager(): Promise<OpenAppCommandResult>;
  pickFile(): Promise<OpenAppCommandResult>;
  pickFolder(): Promise<OpenAppCommandResult>;
  openChrome(): Promise<OpenAppCommandResult>;
  openUrl(options: { url: string; packageName?: string }): Promise<OpenAppCommandResult>;
  openChromeIncognitoBestEffort(): Promise<OpenAppCommandResult>;
  openAmap(): Promise<OpenAppCommandResult>;
  openMapLocation(options: {
    lat: number;
    lng: number;
    name?: string;
  }): Promise<OpenAppCommandResult>;
  openNavigation(options: {
    lat: number;
    lng: number;
    name?: string;
  }): Promise<OpenAppCommandResult>;
  openMapSearch(options: { keyword: string }): Promise<OpenAppCommandResult>;
  openKeep(): Promise<OpenAppCommandResult>;
  openQQMusic(): Promise<OpenAppCommandResult>;
  openMusicLink(options: { url: string }): Promise<OpenAppCommandResult>;
  openWeChat(): Promise<OpenAppCommandResult>;
  shareText(options: { text: string }): Promise<OpenAppCommandResult>;
  shareUrl(options: { url: string }): Promise<OpenAppCommandResult>;
  openClash(): Promise<OpenAppCommandResult>;
  openGallery(): Promise<OpenAppCommandResult>;
  pickImage(): Promise<OpenAppCommandResult>;
  pickVideo(): Promise<OpenAppCommandResult>;
  openBilibili(): Promise<OpenAppCommandResult>;
  openBiliUrl(options: { url: string }): Promise<OpenAppCommandResult>;
  openAuthenticator(): Promise<OpenAppCommandResult>;
  openRecorder(): Promise<OpenAppCommandResult>;
  recordSound(): Promise<OpenAppCommandResult>;
  openWeather(): Promise<OpenAppCommandResult>;
  openCamera(): Promise<OpenAppCommandResult>;
  takePhoto(): Promise<OpenAppCommandResult>;
  takeVideo(): Promise<OpenAppCommandResult>;
  openDoubao(): Promise<OpenAppCommandResult>;
  shareToDoubao(options: { text: string }): Promise<OpenAppCommandResult>;
  openPackage(options: { packageName: string }): Promise<OpenAppCommandResult>;
  launchIntent(options: LaunchIntentOptions): Promise<OpenAppCommandResult>;
  hasOverlayPermission(): Promise<OverlayPermissionResult>;
  hasAccessibilityPermission(): Promise<AccessibilityPermissionResult>;
  openOverlayPermissionSettings(): Promise<FocusOverlayResult>;
  openAccessibilitySettings(): Promise<FocusOverlayResult>;
  startFocusOverlay(): Promise<FocusOverlayResult>;
  stopFocusOverlay(): Promise<FocusOverlayResult>;
  getWindowFocusInfo(): Promise<WindowFocusInfoResult>;
}

export const MiuiPower = registerPlugin<MiuiPowerPlugin>("MiuiPower");
