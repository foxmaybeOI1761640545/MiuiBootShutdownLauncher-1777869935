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

export interface OverlayPermissionResult {
  granted: boolean;
}

export interface FocusOverlayResult {
  ok: boolean;
  method: "focus_overlay_service" | "overlay_permission_required" | "manage_overlay_permission" | "none";
  permissionRequired?: boolean;
  error?: string;
}

export interface WindowFocusInfoResult {
  ok: boolean;
  command: string;
  lines: string[];
  raw: string;
  error: string;
  exitCode: number;
  timedOut: boolean;
}

export interface MiuiPowerPlugin {
  openBootShutdownPage(): Promise<OpenBootShutdownResult>;
  openWirelessDebuggingPage(): Promise<OpenWirelessDebuggingResult>;
  openDeveloperOptions(): Promise<OpenDeveloperOptionsResult>;
  hasOverlayPermission(): Promise<OverlayPermissionResult>;
  openOverlayPermissionSettings(): Promise<FocusOverlayResult>;
  startFocusOverlay(): Promise<FocusOverlayResult>;
  stopFocusOverlay(): Promise<FocusOverlayResult>;
  getWindowFocusInfo(): Promise<WindowFocusInfoResult>;
}

export const MiuiPower = registerPlugin<MiuiPowerPlugin>("MiuiPower");
