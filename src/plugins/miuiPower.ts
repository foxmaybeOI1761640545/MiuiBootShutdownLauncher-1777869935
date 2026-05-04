import { registerPlugin } from "@capacitor/core";

export interface OpenBootShutdownResult {
  ok: boolean;
  method: "action" | "component" | "fallback" | "none";
}

export interface MiuiPowerPlugin {
  openBootShutdownPage(): Promise<OpenBootShutdownResult>;
}

export const MiuiPower = registerPlugin<MiuiPowerPlugin>("MiuiPower");
