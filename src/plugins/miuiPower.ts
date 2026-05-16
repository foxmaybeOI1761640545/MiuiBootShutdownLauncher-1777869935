import { registerPlugin, type PluginListenerHandle } from "@capacitor/core";

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

export interface DisplayRefreshRateResult {
  currentRefreshRate: number;
  roundedRefreshRate: number;
  supportedRefreshRates: number[];
}

export type HeartRateStatus =
  | "idle"
  | "permission_required"
  | "bluetooth_off"
  | "scanning"
  | "connecting"
  | "connected"
  | "recording"
  | "reconnecting"
  | "disconnected"
  | "stopping"
  | "error";

export interface HeartRateDevice {
  address: string;
  name?: string;
  rssi?: number;
  lastSeenMs: number;
  remembered: boolean;
  heartRateServiceAdvertised: boolean;
}

export interface HeartRateSample {
  timestampMs: number;
  sessionId: string;
  deviceAddress: string;
  deviceName?: string;
  bpm: number;
  bpmFormat: "uint8" | "uint16";
  rawHex: string;
  flags: number;
  sensorContactSupported: boolean;
  sensorContactDetected: boolean | null;
  energyExpended?: number | null;
  rrIntervals?: number[];
  rrIntervalsMs?: number[];
  bodySensorLocation?: string | null;
  batteryLevel?: number | null;
}

export interface HeartRateState {
  status: HeartRateStatus;
  device?: HeartRateDevice | null;
  latestSample?: HeartRateSample | null;
  sampleCount: number;
  serviceRunning: boolean;
  foregroundNotificationVisible?: boolean;
  autoReconnectEnabled?: boolean;
  autoRecordingActive?: boolean;
  reconnectAttempt?: number;
  nextReconnectDelayMs?: number | null;
  recording: boolean;
  sessionId?: string | null;
  error?: string;
  bodySensorLocation?: string;
  batteryLevel?: number | null;
  lastNativeUpdateMs?: number;
  lastSampleTimestampMs?: number | null;
}

export interface AutoHeartRateSettings {
  ok?: boolean;
  method?: string;
  enabled: boolean;
  targetDeviceName: string;
  targetDeviceAddress?: string | null;
  chunkSize: number;
  overlapRows: number;
  uploadCsv: boolean;
  uploadJsonl: boolean;
  deleteLocalAfterUpload: boolean;
  retryIntervalMs: number;
  failureNotifyThreshold: number;
}

export interface HeartRateUploadFailureRecord {
  timestampMs: number;
  chunkId: string;
  fileType: "csv" | "jsonl";
  attempt: number;
  errorType: string;
  httpCode?: number | null;
  message: string;
}

export interface AutoHeartRateState {
  enabled: boolean;
  targetName: string;
  targetAddress?: string | null;
  serviceRunning: boolean;
  bluetoothOn: boolean;
  scanning: boolean;
  connecting: boolean;
  connected: boolean;
  recording: boolean;
  currentChunkRows: number;
  chunkSize: number;
  overlapRows: number;
  pendingUploadChunks: number;
  uploadRunning: boolean;
  consecutiveUploadFailures: number;
  lastUploadError?: string;
  lastThreeUploadErrors: HeartRateUploadFailureRecord[];
  lastNativeUpdateMs?: number;
  managerStatus?: string;
}

export interface HeartRateUploadQueueState {
  pendingChunks: number;
  uploadRunning: boolean;
  consecutiveUploadFailures: number;
  lastThreeUploadErrors: HeartRateUploadFailureRecord[];
  currentChunkId?: string | null;
  currentStatus: string;
}

export interface HeartRatePermissionResult {
  granted: boolean;
  requiredPermissions: string[];
  bleGranted?: boolean;
  notificationGranted?: boolean;
}

export interface HeartRateScanResult {
  devices: HeartRateDevice[];
  usedFallback?: boolean;
}

export interface LastHeartRateDeviceResult {
  device?: HeartRateDevice | null;
}

export interface HeartRateHistoryResult {
  samples: HeartRateSample[];
}

export type HeartRateExportFormat = "jsonl" | "csv";
export type HeartRateShareTarget = "system" | "wechat";

export interface HeartRateExportResult {
  ok: boolean;
  method: string;
  format?: HeartRateExportFormat;
  contentUri?: string;
  fileName?: string;
  mimeType?: string;
  rowCount?: number;
  sizeBytes?: number;
  createdAtMs?: number;
  savedUri?: string;
  target?: HeartRateShareTarget;
  statusCode?: number;
  path?: string;
  error?: string;
}

export interface HeartRateStorageStats {
  historyRows: number;
  historySizeBytes: number;
  sessionsRows: number;
  sessionsSizeBytes: number;
  exportFileCount: number;
  exportCacheSizeBytes: number;
  totalHeartRateSizeBytes: number;
  autoCurrentChunkRows?: number;
  autoPendingChunkCount?: number;
  autoFailedChunkCount?: number;
  autoPendingUploadSizeBytes?: number;
  autoUploadedSummaryCount?: number;
}

export interface GitHubExportSettings {
  owner: string;
  repo: string;
  branch: string;
  pathPrefix: string;
  tokenSaved: boolean;
}

export interface GitHubExportSettingsResult extends GitHubExportSettings {
  ok: boolean;
  method: string;
  statusCode?: number;
  error?: string;
}

export interface GitHubUploadResult {
  ok: boolean;
  method: string;
  fileName?: string;
  path?: string;
  rowCount?: number;
  commitSha?: string;
  htmlUrl?: string;
  statusCode?: number;
  error?: string;
}

export interface OpenAppIntentResult {
  ok: boolean;
  method: string;
  openPage?: string;
  fromNotification?: boolean;
  error?: string;
}

export interface ClipboardTextResult {
  ok: boolean;
  hasText: boolean;
  text?: string;
  method: string;
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
  openDisplaySettings(): Promise<OpenAppCommandResult>;
  getDisplayRefreshRate(): Promise<DisplayRefreshRateResult>;
  getClipboardText(): Promise<ClipboardTextResult>;
  getHeartRateState(): Promise<HeartRateState>;
  requestHeartRatePermissions(): Promise<HeartRatePermissionResult>;
  scanHeartRateDevices(options: { durationMs?: number }): Promise<HeartRateScanResult>;
  getLastHeartRateDevice(): Promise<LastHeartRateDeviceResult>;
  connectHeartRateDevice(options: { address: string; name?: string }): Promise<OpenAppCommandResult>;
  disconnectHeartRateDevice(): Promise<OpenAppCommandResult>;
  startHeartRateRecording(): Promise<OpenAppCommandResult>;
  stopHeartRateRecording(): Promise<OpenAppCommandResult>;
  getHeartRateServiceState(): Promise<HeartRateState>;
  setHeartRateAutoReconnect(options: { enabled: boolean }): Promise<OpenAppCommandResult>;
  getAutoHeartRateSettings(): Promise<AutoHeartRateSettings>;
  saveAutoHeartRateSettings(settings: Partial<AutoHeartRateSettings>): Promise<OpenAppCommandResult>;
  enableAutoHeartRateMode(): Promise<OpenAppCommandResult>;
  disableAutoHeartRateMode(): Promise<OpenAppCommandResult>;
  getAutoHeartRateState(): Promise<AutoHeartRateState>;
  getHeartRateUploadQueue(): Promise<HeartRateUploadQueueState>;
  retryHeartRateUploadNow(): Promise<OpenAppCommandResult>;
  clearUploadedLocalChunks(): Promise<OpenAppCommandResult>;
  consumeOpenAppIntent(): Promise<OpenAppIntentResult>;
  exportHeartRateHistory(options: {
    format: HeartRateExportFormat;
    sinceMs?: number;
    untilMs?: number;
  }): Promise<HeartRateExportResult>;
  getLastHeartRateExport(): Promise<HeartRateExportResult>;
  shareHeartRateExport(options: {
    format: HeartRateExportFormat;
    target?: HeartRateShareTarget;
  }): Promise<HeartRateExportResult>;
  saveHeartRateExportToDownloads(options: { format: HeartRateExportFormat }): Promise<HeartRateExportResult>;
  markFrontendReady(options: { page: string; timestampMs: number }): Promise<OpenAppCommandResult>;
  saveGitHubExportSettings(options: {
    owner: string;
    repo: string;
    branch: string;
    pathPrefix: string;
    token?: string;
  }): Promise<GitHubExportSettingsResult>;
  getGitHubExportSettings(): Promise<GitHubExportSettingsResult>;
  testGitHubExportSettings(): Promise<GitHubExportSettingsResult>;
  uploadHeartRateExportToGitHub(options: {
    format: HeartRateExportFormat;
    path?: string;
  }): Promise<GitHubUploadResult>;
  getHeartRateHistory(options?: { limit?: number; sinceMs?: number }): Promise<HeartRateHistoryResult>;
  getHeartRateStorageStats(): Promise<HeartRateStorageStats>;
  clearHeartRateHistory(): Promise<OpenAppCommandResult>;
  clearHeartRateExportCache(): Promise<OpenAppCommandResult>;
  clearAllHeartRateData(): Promise<OpenAppCommandResult>;
  addListener(
    eventName: "heartRateStateChanged",
    listenerFunc: (state: HeartRateState) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: "heartRateDeviceFound",
    listenerFunc: (device: HeartRateDevice) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: "heartRateSample",
    listenerFunc: (sample: HeartRateSample) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: "autoHeartRateStateChanged",
    listenerFunc: (state: AutoHeartRateState) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: "heartRateUploadQueueChanged",
    listenerFunc: (state: HeartRateUploadQueueState) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: "heartRateUploadAlert",
    listenerFunc: (state: { consecutiveUploadFailures: number; lastThreeUploadErrors: HeartRateUploadFailureRecord[] }) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: "heartRateChunkClosed" | "heartRateChunkUploaded",
    listenerFunc: (state: Record<string, unknown>) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: "heartRateUploadFailed",
    listenerFunc: (failure: HeartRateUploadFailureRecord & { state?: HeartRateUploadQueueState }) => void,
  ): Promise<PluginListenerHandle>;
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
