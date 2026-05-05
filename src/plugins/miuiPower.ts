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

export interface MiuiPowerPlugin {
  openBootShutdownPage(): Promise<OpenBootShutdownResult>;
  openWirelessDebuggingPage(): Promise<OpenWirelessDebuggingResult>;
  openDeveloperOptions(): Promise<OpenDeveloperOptionsResult>;
}

export const MiuiPower = registerPlugin<MiuiPowerPlugin>("MiuiPower");
