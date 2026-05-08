<template>
  <main class="page">
    <section class="launcher-shell">
      <header class="app-header">
        <h1>Launcher</h1>
      </header>

      <section class="action-stage">
        <article class="primary-panel">
          <div class="action-rows">
            <div class="action-row" v-for="(row, rowIndex) in activeRows" :key="`${activePage}-${rowIndex}`">
              <button
                v-for="cell in row"
                :key="cell.key"
                type="button"
                class="action-btn"
                :class="[
                  spanClass(cell.span),
                  variantClass(actionByKey[cell.key].variant),
                  { 'is-disabled': isActionDisabled(cell.key) },
                ]"
                :disabled="isLoading || isActionDisabled(cell.key)"
                @click="runAction(cell.key)"
              >
                {{ buttonLabel(cell.key) }}
              </button>
            </div>
          </div>
        </article>

        <article v-if="activeExtras.length > 0" class="extra-panel">
          <h2>更多功能</h2>
          <div class="extra-grid">
            <button
              v-for="key in activeExtras"
              :key="key"
              type="button"
              class="action-btn action-btn--compact"
              :class="[variantClass(actionByKey[key].variant), { 'is-disabled': isActionDisabled(key) }]"
              :disabled="isLoading || isActionDisabled(key)"
              @click="runAction(key)"
            >
              {{ buttonLabel(key) }}
            </button>
          </div>
        </article>
      </section>

      <section class="status-panel">
        <p class="status-label">最近操作</p>
        <p class="status-message">{{ displayMessage }}</p>
      </section>

      <nav class="bottom-nav" aria-label="页面导航">
        <button
          v-for="item in navItems"
          :key="item.id"
          type="button"
          class="nav-btn"
          :class="{ active: activePage === item.id }"
          @click="activePage = item.id"
        >
          <span class="nav-btn-bg">
            <img :src="item.icon" :alt="item.label" class="nav-icon" />
          </span>
        </button>
      </nav>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import type { OpenAppCommandResult } from "./plugins/miuiPower";
import { MiuiPower } from "./plugins/miuiPower";
import page1Icon from "./assets/nav-page-1.svg";
import page2Icon from "./assets/nav-page-2.svg";
import page3Icon from "./assets/nav-page-3.svg";

type PageId = "page1" | "page2" | "page3";
type ActionVariant = "pink" | "beige";
type CellSpan = "half" | "full";

interface ActionItem {
  key: string;
  label: string;
  loadingLabel: string;
  variant: ActionVariant;
  run?: () => Promise<void>;
  disabled?: boolean;
}

interface LayoutCell {
  key: string;
  span: CellSpan;
}

interface NavItem {
  id: PageId;
  label: string;
  icon: string;
}

const activePage = ref<PageId>("page1");
const loadingAction = ref("");
const message = ref("");

const isLoading = computed(() => loadingAction.value !== "");
const displayMessage = computed(() => message.value || "等待操作，请点击一个入口按钮。");

function spanClass(span: CellSpan) {
  return span === "full" ? "span-full" : "span-half";
}

function variantClass(variant: ActionVariant) {
  return variant === "beige" ? "tone-beige" : "tone-pink";
}

function buttonLabel(key: string) {
  const action = actionByKey[key];
  if (!action) {
    return key;
  }
  return loadingAction.value === key ? action.loadingLabel : action.label;
}

function isActionDisabled(key: string) {
  const action = actionByKey[key];
  return !action || action.disabled === true || !action.run;
}

async function runAction(key: string) {
  const action = actionByKey[key];
  if (!action || action.disabled || !action.run || isLoading.value) {
    return;
  }
  await action.run();
}

function getErrorMessage(error: unknown) {
  return typeof error === "object" && error !== null && "message" in error
    ? String((error as { message?: string }).message ?? "")
    : "";
}

async function runWithLoading(key: string, fallbackMessage: string, task: () => Promise<string>) {
  loadingAction.value = key;
  message.value = "";

  try {
    message.value = await task();
  } catch (error) {
    message.value = getErrorMessage(error) || fallbackMessage;
  } finally {
    loadingAction.value = "";
  }
}

function formatOpenResult(label: string, result: OpenAppCommandResult) {
  if (result.ok) {
    return `${label}：已触发（${result.method}）`;
  }

  if (result.installed === false && result.packageName) {
    return `${label}：未安装 ${result.packageName}`;
  }

  const errorSuffix = result.error ? `，${result.error}` : "";
  return `${label}：失败（${result.method}${errorSuffix}）`;
}

async function runSimpleOpenCommand(
  key: string,
  label: string,
  command: () => Promise<OpenAppCommandResult>,
) {
  await runWithLoading(key, `${label}失败`, async () => {
    const result = await command();
    return formatOpenResult(label, result);
  });
}

async function openBootShutdownPage() {
  await runWithLoading("bootShutdown", "无法打开定时开关机页面", async () => {
    const result = await MiuiPower.openBootShutdownPage();
    return result.ok ? `定时开关机：已尝试打开（${result.method}）` : "定时开关机：打开失败";
  });
}

async function openWirelessDebuggingPage() {
  await runWithLoading("wirelessDebugging", "无法打开无线调试页面", async () => {
    const result = await MiuiPower.openWirelessDebuggingPage();
    return result.ok ? `无线调试：已尝试打开（${result.method}）` : "无线调试：打开失败";
  });
}

async function openDeveloperOptionsPage() {
  await runWithLoading("developerOptions", "无法打开开发者选项", async () => {
    const result = await MiuiPower.openDeveloperOptions();
    return result.ok ? `开发者选项：已尝试打开（${result.method}）` : "开发者选项：打开失败";
  });
}

async function openScreenRefreshRatePage() {
  await runWithLoading("screenRefreshRate", "无法打开屏幕刷新率设置", async () => {
    const result = await MiuiPower.openScreenRefreshRatePage();
    return result.ok ? `屏幕刷新率：已尝试打开（${result.method}）` : "屏幕刷新率：打开失败";
  });
}

async function openHonorOfKings() {
  await runWithLoading("honorOfKings", "无法启动王者荣耀", async () => {
    const result = await MiuiPower.openHonorOfKings();
    if (result.ok) {
      return "王者荣耀：已尝试启动";
    }
    if (!result.installed) {
      return "王者荣耀：未检测到安装（com.tencent.tmgp.sgame）";
    }
    return `王者荣耀：启动失败${result.error ? `，${result.error}` : ""}`;
  });
}

async function startFocusOverlay() {
  await runWithLoading("focusOverlay", "无法启动焦点悬浮按钮", async () => {
    const result = await MiuiPower.startFocusOverlay();

    if (result.overlayPermissionRequired || result.permissionRequired) {
      return "焦点悬浮按钮：需要先授予悬浮窗权限（已尝试打开授权页）";
    }
    if (result.ok && result.accessibilityPermissionRequired) {
      return "焦点悬浮按钮：已启动，但需先开启无障碍服务";
    }
    if (result.ok) {
      return "焦点悬浮按钮：已启动";
    }
    return `焦点悬浮按钮：启动失败${result.error ? `，${result.error}` : ""}`;
  });
}

async function openAccessibilitySettings() {
  await runWithLoading("accessibility", "无法打开无障碍设置", async () => {
    const result = await MiuiPower.openAccessibilitySettings();
    return result.ok ? "无障碍设置：已打开" : "无障碍设置：打开失败";
  });
}

async function stopFocusOverlay() {
  await runWithLoading("stopFocusOverlay", "无法关闭焦点悬浮按钮", async () => {
    const result = await MiuiPower.stopFocusOverlay();
    return result.ok ? "焦点悬浮按钮：已关闭" : "焦点悬浮按钮：当前未运行";
  });
}

async function getWindowFocusInfo() {
  await runWithLoading("windowFocus", "读取窗口焦点失败", async () => {
    const result = await MiuiPower.getWindowFocusInfo();
    if (result.ok) {
      return result.lines.join("\n");
    }

    const detail = result.lines.length > 0 ? `\n${result.lines.join("\n")}` : "";
    return `${result.error || "读取窗口焦点失败"}${detail}`;
  });
}

const actionByKey: Record<string, ActionItem> = {
  bootShutdown: {
    key: "bootShutdown",
    label: "定时开关机",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: openBootShutdownPage,
  },
  developerOptions: {
    key: "developerOptions",
    label: "开发者选项",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: openDeveloperOptionsPage,
  },
  accessibility: {
    key: "accessibility",
    label: "无障碍设置",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: openAccessibilitySettings,
  },
  screenRefreshRate: {
    key: "screenRefreshRate",
    label: "分辨率调整",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: openScreenRefreshRatePage,
  },
  wirelessDebugging: {
    key: "wirelessDebugging",
    label: "无线调试",
    loadingLabel: "正在打开...",
    variant: "beige",
    run: openWirelessDebuggingPage,
  },
  focusOverlay: {
    key: "focusOverlay",
    label: "启动焦点",
    loadingLabel: "正在启动...",
    variant: "pink",
    run: startFocusOverlay,
  },
  stopFocusOverlay: {
    key: "stopFocusOverlay",
    label: "关闭焦点",
    loadingLabel: "正在关闭...",
    variant: "pink",
    run: stopFocusOverlay,
  },
  windowFocus: {
    key: "windowFocus",
    label: "读取窗口焦点",
    loadingLabel: "正在读取...",
    variant: "pink",
    run: getWindowFocusInfo,
  },
  screenTimePage: {
    key: "screenTimePage",
    label: "屏幕时间",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("screenTimePage", "屏幕时间页面", () => MiuiPower.openScreenTimePage()),
  },
  usageAccessSettings: {
    key: "usageAccessSettings",
    label: "使用情况权限",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("usageAccessSettings", "使用情况访问权限", () =>
        MiuiPower.openUsageAccessSettings(),
      ),
  },
  honorOfKings: {
    key: "honorOfKings",
    label: "王者荣耀",
    loadingLabel: "正在启动...",
    variant: "pink",
    run: openHonorOfKings,
  },
  openBilibili: {
    key: "openBilibili",
    label: "哔哩哔哩",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("openBilibili", "打开 Bilibili", () => MiuiPower.openBilibili()),
  },
  openWeChat: {
    key: "openWeChat",
    label: "微信",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("openWeChat", "打开微信", () => MiuiPower.openWeChat()),
  },
  qqPlaceholder: {
    key: "qqPlaceholder",
    label: "QQ（待接入）",
    loadingLabel: "待接入",
    variant: "pink",
    disabled: true,
  },
  openQQMusic: {
    key: "openQQMusic",
    label: "QQ音乐",
    loadingLabel: "正在打开...",
    variant: "beige",
    run: () => runSimpleOpenCommand("openQQMusic", "打开 QQ 音乐", () => MiuiPower.openQQMusic()),
  },
  openCamera: {
    key: "openCamera",
    label: "相机",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("openCamera", "打开相机", () => MiuiPower.openCamera()),
  },
  openGallery: {
    key: "openGallery",
    label: "相册",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("openGallery", "打开相册", () => MiuiPower.openGallery()),
  },
  morePlaceholderLeft: {
    key: "morePlaceholderLeft",
    label: "...",
    loadingLabel: "...",
    variant: "pink",
    disabled: true,
  },
  morePlaceholderRight: {
    key: "morePlaceholderRight",
    label: "...",
    loadingLabel: "...",
    variant: "pink",
    disabled: true,
  },
  openFileManager: {
    key: "openFileManager",
    label: "文件管理",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("openFileManager", "文件管理", () => MiuiPower.openFileManager()),
  },
  pickFile: {
    key: "pickFile",
    label: "选择文件",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("pickFile", "选择文件", () => MiuiPower.pickFile()),
  },
  pickFolder: {
    key: "pickFolder",
    label: "选择目录",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("pickFolder", "选择目录", () => MiuiPower.pickFolder()),
  },
  openChrome: {
    key: "openChrome",
    label: "打开 Chrome",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("openChrome", "打开 Chrome", () => MiuiPower.openChrome()),
  },
  openUrl: {
    key: "openUrl",
    label: "URL 示例",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("openUrl", "Chrome 打开 URL", () =>
        MiuiPower.openUrl({ url: "https://www.google.com", packageName: "com.android.chrome" }),
      ),
  },
  openChromeIncognito: {
    key: "openChromeIncognito",
    label: "Chrome 无痕",
    loadingLabel: "正在尝试...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("openChromeIncognito", "Chrome 无痕", () =>
        MiuiPower.openChromeIncognitoBestEffort(),
      ),
  },
  openAmap: {
    key: "openAmap",
    label: "高德地图",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("openAmap", "打开高德地图", () => MiuiPower.openAmap()),
  },
  openMapLocation: {
    key: "openMapLocation",
    label: "高德定位",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("openMapLocation", "高德定位", () =>
        MiuiPower.openMapLocation({ lat: 31.2304, lng: 121.4737, name: "上海市人民广场" }),
      ),
  },
  openNavigation: {
    key: "openNavigation",
    label: "高德导航",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("openNavigation", "高德导航", () =>
        MiuiPower.openNavigation({ lat: 31.2304, lng: 121.4737, name: "上海市人民广场" }),
      ),
  },
  openMapSearch: {
    key: "openMapSearch",
    label: "高德搜索",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("openMapSearch", "高德搜索", () =>
        MiuiPower.openMapSearch({ keyword: "外滩" }),
      ),
  },
  openKeep: {
    key: "openKeep",
    label: "Keep",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("openKeep", "打开 Keep", () => MiuiPower.openKeep()),
  },
  openMusicLink: {
    key: "openMusicLink",
    label: "音乐链接",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("openMusicLink", "打开音乐链接", () =>
        MiuiPower.openMusicLink({ url: "https://y.qq.com" }),
      ),
  },
  shareText: {
    key: "shareText",
    label: "分享文本",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("shareText", "系统分享文本", () =>
        MiuiPower.shareText({ text: "这是一条来自 MIUI Intent Launcher 的分享文本" }),
      ),
  },
  shareUrl: {
    key: "shareUrl",
    label: "分享链接",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("shareUrl", "系统分享链接", () =>
        MiuiPower.shareUrl({ url: "https://www.bilibili.com" }),
      ),
  },
  openClash: {
    key: "openClash",
    label: "Clash",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("openClash", "打开 Clash", () => MiuiPower.openClash()),
  },
  openDoubao: {
    key: "openDoubao",
    label: "豆包",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("openDoubao", "打开豆包", () => MiuiPower.openDoubao()),
  },
  shareToDoubao: {
    key: "shareToDoubao",
    label: "分享到豆包",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("shareToDoubao", "分享文本到豆包", () =>
        MiuiPower.shareToDoubao({ text: "请帮我总结今天的工作要点。" }),
      ),
  },
  openBiliUrl: {
    key: "openBiliUrl",
    label: "B站链接",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("openBiliUrl", "打开 B 站链接", () =>
        MiuiPower.openBiliUrl({ url: "https://www.bilibili.com/video/BV1GJ411x7h7" }),
      ),
  },
  openWeather: {
    key: "openWeather",
    label: "天气",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("openWeather", "打开天气", () => MiuiPower.openWeather()),
  },
  openRecorder: {
    key: "openRecorder",
    label: "录音机",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("openRecorder", "打开录音机", () => MiuiPower.openRecorder()),
  },
  recordSound: {
    key: "recordSound",
    label: "系统录音",
    loadingLabel: "正在打开...",
    variant: "beige",
    run: () => runSimpleOpenCommand("recordSound", "系统录音", () => MiuiPower.recordSound()),
  },
  openAuthenticator: {
    key: "openAuthenticator",
    label: "Authenticator",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () =>
      runSimpleOpenCommand("openAuthenticator", "打开 Authenticator", () =>
        MiuiPower.openAuthenticator(),
      ),
  },
  takePhoto: {
    key: "takePhoto",
    label: "拍照",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("takePhoto", "拍照", () => MiuiPower.takePhoto()),
  },
  takeVideo: {
    key: "takeVideo",
    label: "录像",
    loadingLabel: "正在打开...",
    variant: "pink",
    run: () => runSimpleOpenCommand("takeVideo", "录像", () => MiuiPower.takeVideo()),
  },
  pickImage: {
    key: "pickImage",
    label: "选择图片",
    loadingLabel: "正在打开...",
    variant: "beige",
    run: () => runSimpleOpenCommand("pickImage", "选择图片", () => MiuiPower.pickImage()),
  },
  pickVideo: {
    key: "pickVideo",
    label: "选择视频",
    loadingLabel: "正在打开...",
    variant: "beige",
    run: () => runSimpleOpenCommand("pickVideo", "选择视频", () => MiuiPower.pickVideo()),
  },
};

const pageLayouts: Record<PageId, LayoutCell[][]> = {
  page1: [
    [
      { key: "bootShutdown", span: "half" },
      { key: "developerOptions", span: "half" },
    ],
    [
      { key: "accessibility", span: "half" },
      { key: "screenRefreshRate", span: "half" },
    ],
    [{ key: "wirelessDebugging", span: "full" }],
    [
      { key: "focusOverlay", span: "half" },
      { key: "stopFocusOverlay", span: "half" },
    ],
  ],
  page2: [
    [
      { key: "honorOfKings", span: "half" },
      { key: "openBilibili", span: "half" },
    ],
    [
      { key: "openWeChat", span: "half" },
      { key: "qqPlaceholder", span: "half" },
    ],
    [{ key: "openQQMusic", span: "full" }],
    [
      { key: "openCamera", span: "half" },
      { key: "openGallery", span: "half" },
    ],
    [
      { key: "morePlaceholderLeft", span: "half" },
      { key: "morePlaceholderRight", span: "half" },
    ],
  ],
  page3: [
    [
      { key: "openWeather", span: "half" },
      { key: "openRecorder", span: "half" },
    ],
    [
      { key: "recordSound", span: "half" },
      { key: "openAuthenticator", span: "half" },
    ],
    [
      { key: "takePhoto", span: "half" },
      { key: "takeVideo", span: "half" },
    ],
    [
      { key: "pickImage", span: "half" },
      { key: "pickVideo", span: "half" },
    ],
  ],
};

const pageExtras: Record<PageId, string[]> = {
  page1: ["windowFocus", "screenTimePage", "usageAccessSettings"],
  page2: [
    "openFileManager",
    "pickFile",
    "pickFolder",
    "openChrome",
    "openUrl",
    "openChromeIncognito",
    "openAmap",
    "openMapLocation",
    "openNavigation",
    "openMapSearch",
    "openKeep",
    "openMusicLink",
    "shareText",
    "shareUrl",
    "openClash",
    "openDoubao",
    "shareToDoubao",
    "openBiliUrl",
  ],
  page3: [],
};

const navItems: NavItem[] = [
  { id: "page1", label: "页面一", icon: page1Icon },
  { id: "page2", label: "页面二", icon: page2Icon },
  { id: "page3", label: "页面三", icon: page3Icon },
];

const activeRows = computed(() => pageLayouts[activePage.value]);
const activeExtras = computed(() => pageExtras[activePage.value]);
</script>
