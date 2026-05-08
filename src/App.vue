<template>
  <main class="page" :data-theme="theme">
    <div class="app-shell">
      <header class="toolbar">
        <div class="profile-chip">
          <span class="avatar-wrap">
            <span class="avatar">MIUI</span>
          </span>
          <span class="profile-name">Intent Launcher</span>
        </div>

        <button type="button" class="mode-switch" @click="toggleTheme">
          <span class="switch-track">
            <span class="switch-thumb" :class="{ on: theme === 'night' }"></span>
          </span>
        </button>
      </header>

      <section class="panel hero-panel">
        <h1>MIUI System Entry Launcher</h1>
        <p class="description">
          普通权限场景（无 root / 无 ADB / 无系统签名）下的 Intent 入口实验面板。
        </p>

        <h2 class="entry-title">原有系统能力</h2>
        <div class="actions compact">
          <button
            v-for="action in coreActions"
            :key="action.key"
            type="button"
            :disabled="isLoading"
            @click="action.run"
          >
            {{ buttonLabel(action) }}
          </button>
        </div>
      </section>

      <section class="panel entry-panel">
        <h2 class="entry-title">补充应用入口按键</h2>
        <div class="entry-groups">
          <article class="entry-group" v-for="group in entryGroups" :key="group.title">
            <h3>{{ group.title }}</h3>
            <div class="actions compact">
              <button
                v-for="action in group.actions"
                :key="action.key"
                type="button"
                :disabled="isLoading"
                @click="action.run"
              >
                {{ buttonLabel(action) }}
              </button>
            </div>
          </article>
        </div>
      </section>

      <section class="panel status-panel">
        <p class="status-title">最近操作</p>
        <p class="message">{{ displayMessage }}</p>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import type { OpenAppCommandResult } from "./plugins/miuiPower";
import { MiuiPower } from "./plugins/miuiPower";

interface ActionButton {
  key: string;
  label: string;
  loadingLabel: string;
  run: () => Promise<void>;
}

interface ActionGroup {
  title: string;
  actions: ActionButton[];
}

const theme = ref<"day" | "night">("day");
const loadingAction = ref("");
const message = ref("");

const isLoading = computed(() => loadingAction.value !== "");
const displayMessage = computed(() => {
  return message.value || "等待操作，请点击一个入口按钮。";
});

function toggleTheme() {
  theme.value = theme.value === "day" ? "night" : "day";
}

function buttonLabel(action: ActionButton) {
  return loadingAction.value === action.key ? action.loadingLabel : action.label;
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
    return result.ok
      ? `定时开关机：已尝试打开（${result.method}）`
      : "定时开关机：打开失败";
  });
}

async function openWirelessDebuggingPage() {
  await runWithLoading("wirelessDebugging", "无法打开无线调试页面", async () => {
    const result = await MiuiPower.openWirelessDebuggingPage();
    return result.ok
      ? `无线调试：已尝试打开（${result.method}）`
      : "无线调试：打开失败";
  });
}

async function openDeveloperOptionsPage() {
  await runWithLoading("developerOptions", "无法打开开发者选项", async () => {
    const result = await MiuiPower.openDeveloperOptions();
    return result.ok
      ? `开发者选项：已尝试打开（${result.method}）`
      : "开发者选项：打开失败";
  });
}

async function openScreenRefreshRatePage() {
  await runWithLoading("screenRefreshRate", "无法打开屏幕刷新率设置", async () => {
    const result = await MiuiPower.openScreenRefreshRatePage();
    return result.ok
      ? `屏幕刷新率：已尝试打开（${result.method}）`
      : "屏幕刷新率：打开失败";
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

function getErrorMessage(error: unknown) {
  return typeof error === "object" && error !== null && "message" in error
    ? String((error as { message?: string }).message ?? "")
    : "";
}

const coreActions: ActionButton[] = [
  {
    key: "bootShutdown",
    label: "打开定时开关机",
    loadingLabel: "正在打开...",
    run: openBootShutdownPage,
  },
  {
    key: "wirelessDebugging",
    label: "打开无线调试",
    loadingLabel: "正在打开...",
    run: openWirelessDebuggingPage,
  },
  {
    key: "developerOptions",
    label: "打开开发者选项",
    loadingLabel: "正在打开...",
    run: openDeveloperOptionsPage,
  },
  {
    key: "screenRefreshRate",
    label: "打开屏幕刷新率",
    loadingLabel: "正在打开...",
    run: openScreenRefreshRatePage,
  },
  {
    key: "honorOfKings",
    label: "启动王者荣耀",
    loadingLabel: "正在启动...",
    run: openHonorOfKings,
  },
  {
    key: "focusOverlay",
    label: "启动焦点悬浮按钮",
    loadingLabel: "正在启动...",
    run: startFocusOverlay,
  },
  {
    key: "accessibility",
    label: "打开无障碍设置",
    loadingLabel: "正在打开...",
    run: openAccessibilitySettings,
  },
  {
    key: "windowFocus",
    label: "读取窗口焦点",
    loadingLabel: "正在读取...",
    run: getWindowFocusInfo,
  },
  {
    key: "stopFocusOverlay",
    label: "关闭焦点悬浮按钮",
    loadingLabel: "正在关闭...",
    run: stopFocusOverlay,
  },
];

const entryGroups: ActionGroup[] = [
  {
    title: "屏幕时间与文件",
    actions: [
      {
        key: "screenTimePage",
        label: "打开屏幕时间页面",
        loadingLabel: "正在打开...",
        run: () =>
          runSimpleOpenCommand("screenTimePage", "屏幕时间页面", () => MiuiPower.openScreenTimePage()),
      },
      {
        key: "usageAccessSettings",
        label: "打开使用情况访问权限",
        loadingLabel: "正在打开...",
        run: () =>
          runSimpleOpenCommand("usageAccessSettings", "使用情况访问权限", () =>
            MiuiPower.openUsageAccessSettings(),
          ),
      },
      {
        key: "fileManager",
        label: "打开文件管理",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("fileManager", "文件管理", () => MiuiPower.openFileManager()),
      },
      {
        key: "pickFile",
        label: "选择文件",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("pickFile", "选择文件", () => MiuiPower.pickFile()),
      },
      {
        key: "pickFolder",
        label: "选择目录",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("pickFolder", "选择目录", () => MiuiPower.pickFolder()),
      },
    ],
  },
  {
    title: "Chrome 与地图",
    actions: [
      {
        key: "openChrome",
        label: "打开 Chrome",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("openChrome", "打开 Chrome", () => MiuiPower.openChrome()),
      },
      {
        key: "openUrl",
        label: "Chrome 打开 URL 示例",
        loadingLabel: "正在打开...",
        run: () =>
          runSimpleOpenCommand("openUrl", "Chrome 打开 URL", () =>
            MiuiPower.openUrl({
              url: "https://www.google.com",
              packageName: "com.android.chrome",
            }),
          ),
      },
      {
        key: "openChromeIncognito",
        label: "尝试打开 Chrome 无痕",
        loadingLabel: "正在尝试...",
        run: () =>
          runSimpleOpenCommand("openChromeIncognito", "Chrome 无痕", () =>
            MiuiPower.openChromeIncognitoBestEffort(),
          ),
      },
      {
        key: "openAmap",
        label: "打开高德地图",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("openAmap", "打开高德地图", () => MiuiPower.openAmap()),
      },
      {
        key: "openMapLocation",
        label: "高德定位示例",
        loadingLabel: "正在打开...",
        run: () =>
          runSimpleOpenCommand("openMapLocation", "高德定位", () =>
            MiuiPower.openMapLocation({
              lat: 31.2304,
              lng: 121.4737,
              name: "上海市人民广场",
            }),
          ),
      },
      {
        key: "openNavigation",
        label: "高德导航示例",
        loadingLabel: "正在打开...",
        run: () =>
          runSimpleOpenCommand("openNavigation", "高德导航", () =>
            MiuiPower.openNavigation({
              lat: 31.2304,
              lng: 121.4737,
              name: "上海市人民广场",
            }),
          ),
      },
      {
        key: "openMapSearch",
        label: "高德搜索示例",
        loadingLabel: "正在打开...",
        run: () =>
          runSimpleOpenCommand("openMapSearch", "高德搜索", () =>
            MiuiPower.openMapSearch({ keyword: "外滩" }),
          ),
      },
    ],
  },
  {
    title: "社交、音乐与内容",
    actions: [
      {
        key: "openKeep",
        label: "打开 Keep",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("openKeep", "打开 Keep", () => MiuiPower.openKeep()),
      },
      {
        key: "openQQMusic",
        label: "打开 QQ 音乐",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("openQQMusic", "打开 QQ 音乐", () => MiuiPower.openQQMusic()),
      },
      {
        key: "openMusicLink",
        label: "打开音乐链接示例",
        loadingLabel: "正在打开...",
        run: () =>
          runSimpleOpenCommand("openMusicLink", "打开音乐链接", () =>
            MiuiPower.openMusicLink({ url: "https://y.qq.com" }),
          ),
      },
      {
        key: "openWeChat",
        label: "打开微信",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("openWeChat", "打开微信", () => MiuiPower.openWeChat()),
      },
      {
        key: "shareText",
        label: "系统分享文本",
        loadingLabel: "正在打开...",
        run: () =>
          runSimpleOpenCommand("shareText", "系统分享文本", () =>
            MiuiPower.shareText({ text: "这是一条来自 MIUI Intent Launcher 的分享文本" }),
          ),
      },
      {
        key: "shareUrl",
        label: "系统分享链接",
        loadingLabel: "正在打开...",
        run: () =>
          runSimpleOpenCommand("shareUrl", "系统分享链接", () =>
            MiuiPower.shareUrl({ url: "https://www.bilibili.com" }),
          ),
      },
      {
        key: "openBilibili",
        label: "打开 Bilibili",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("openBilibili", "打开 Bilibili", () => MiuiPower.openBilibili()),
      },
      {
        key: "openBiliUrl",
        label: "打开 B 站链接示例",
        loadingLabel: "正在打开...",
        run: () =>
          runSimpleOpenCommand("openBiliUrl", "打开 B 站链接", () =>
            MiuiPower.openBiliUrl({ url: "https://www.bilibili.com/video/BV1GJ411x7h7" }),
          ),
      },
    ],
  },
  {
    title: "工具与系统应用",
    actions: [
      {
        key: "openClash",
        label: "打开 Clash",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("openClash", "打开 Clash", () => MiuiPower.openClash()),
      },
      {
        key: "openGallery",
        label: "打开相册",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("openGallery", "打开相册", () => MiuiPower.openGallery()),
      },
      {
        key: "pickImage",
        label: "选择图片",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("pickImage", "选择图片", () => MiuiPower.pickImage()),
      },
      {
        key: "pickVideo",
        label: "选择视频",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("pickVideo", "选择视频", () => MiuiPower.pickVideo()),
      },
      {
        key: "openAuthenticator",
        label: "打开 Authenticator",
        loadingLabel: "正在打开...",
        run: () =>
          runSimpleOpenCommand("openAuthenticator", "打开 Authenticator", () =>
            MiuiPower.openAuthenticator(),
          ),
      },
      {
        key: "openRecorder",
        label: "打开录音机",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("openRecorder", "打开录音机", () => MiuiPower.openRecorder()),
      },
      {
        key: "recordSound",
        label: "系统录音",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("recordSound", "系统录音", () => MiuiPower.recordSound()),
      },
      {
        key: "openWeather",
        label: "打开天气",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("openWeather", "打开天气", () => MiuiPower.openWeather()),
      },
      {
        key: "openCamera",
        label: "打开相机",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("openCamera", "打开相机", () => MiuiPower.openCamera()),
      },
      {
        key: "takePhoto",
        label: "拍照",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("takePhoto", "拍照", () => MiuiPower.takePhoto()),
      },
      {
        key: "takeVideo",
        label: "录像",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("takeVideo", "录像", () => MiuiPower.takeVideo()),
      },
      {
        key: "openDoubao",
        label: "打开豆包",
        loadingLabel: "正在打开...",
        run: () => runSimpleOpenCommand("openDoubao", "打开豆包", () => MiuiPower.openDoubao()),
      },
      {
        key: "shareToDoubao",
        label: "分享文本到豆包（尝试）",
        loadingLabel: "正在打开...",
        run: () =>
          runSimpleOpenCommand("shareToDoubao", "分享文本到豆包", () =>
            MiuiPower.shareToDoubao({ text: "请帮我总结今天的工作要点。" }),
          ),
      },
    ],
  },
];
</script>
