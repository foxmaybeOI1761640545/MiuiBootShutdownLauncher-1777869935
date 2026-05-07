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
  method: "miui_refresh_rate_action" | "refresh_rate_fragment" | "display_settings" | "none";
}

export interface OpenHonorOfKingsResult {
  ok: boolean;
  method: "package_launch_intent" | "none";
  installed: boolean;
  packageName: string;
  error?: string;
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
  hasOverlayPermission(): Promise<OverlayPermissionResult>;
  hasAccessibilityPermission(): Promise<AccessibilityPermissionResult>;
  openOverlayPermissionSettings(): Promise<FocusOverlayResult>;
  openAccessibilitySettings(): Promise<FocusOverlayResult>;
  startFocusOverlay(): Promise<FocusOverlayResult>;
  stopFocusOverlay(): Promise<FocusOverlayResult>;
  getWindowFocusInfo(): Promise<WindowFocusInfoResult>;
}

export const MiuiPower = registerPlugin<MiuiPowerPlugin>("MiuiPower");
