<template>
  <main class="page" :data-theme="theme">
    <div class="app-shell">
      <header class="toolbar">
        <div class="profile-chip">
          <span class="avatar-wrap">
            <span class="avatar">六字</span>
          </span>
          <span class="profile-name">系统页面入口</span>
        </div>

        <button type="button" class="mode-switch" @click="toggleTheme">
          <span class="switch-track">
            <span class="switch-thumb" :class="{ on: theme === 'night' }"></span>
          </span>
        </button>
      </header>

      <section class="panel hero-panel">
        <h1>MIUI 系统页面 Launcher</h1>
        <p class="description">
          验证普通第三方应用是否可通过 Intent 打开 MIUI 系统设置页面。
        </p>

        <div class="actions">
          <button type="button" :disabled="isLoading" @click="openBootShutdownPage">
            {{ loadingAction === "bootShutdown" ? "正在打开..." : "打开定时开关机页面" }}
          </button>

          <button type="button" :disabled="isLoading" @click="openWirelessDebuggingPage">
            {{
              loadingAction === "wirelessDebugging"
                ? "正在打开..."
                : "打开无线调试页面"
            }}
          </button>

          <button type="button" :disabled="isLoading" @click="openDeveloperOptionsPage">
            {{
              loadingAction === "developerOptions"
                ? "正在打开..."
                : "打开开发者选项"
            }}
          </button>

          <button type="button" :disabled="isLoading" @click="openScreenRefreshRatePage">
            {{
              loadingAction === "screenRefreshRate"
                ? "正在打开..."
                : "打开屏幕刷新率设置"
            }}
          </button>

          <button type="button" :disabled="isLoading" @click="openHonorOfKings">
            {{
              loadingAction === "honorOfKings"
                ? "正在启动..."
                : "启动王者荣耀"
            }}
          </button>

          <button type="button" :disabled="isLoading" @click="startFocusOverlay">
            {{
              loadingAction === "focusOverlay"
                ? "正在启动..."
                : "启动焦点悬浮按钮"
            }}
          </button>

          <button type="button" :disabled="isLoading" @click="openAccessibilitySettings">
            {{
              loadingAction === "accessibility"
                ? "正在打开..."
                : "打开无障碍设置"
            }}
          </button>

          <button type="button" :disabled="isLoading" @click="getWindowFocusInfo">
            {{
              loadingAction === "windowFocus"
                ? "正在读取..."
                : "立即读取窗口焦点"
            }}
          </button>

          <button type="button" :disabled="isLoading" @click="stopFocusOverlay">
            关闭焦点悬浮按钮
          </button>
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
import { MiuiPower } from "./plugins/miuiPower";

const theme = ref<"day" | "night">("day");
const loadingAction = ref<
  ""
  | "bootShutdown"
  | "wirelessDebugging"
  | "developerOptions"
  | "screenRefreshRate"
  | "honorOfKings"
  | "focusOverlay"
  | "accessibility"
  | "windowFocus"
>("");
const message = ref("");
const isLoading = computed(() => loadingAction.value !== "");
const displayMessage = computed(
  () => message.value || "等待操作，请选择一个系统页面入口。",
);

function toggleTheme() {
  theme.value = theme.value === "day" ? "night" : "day";
}

async function openBootShutdownPage() {
  loadingAction.value = "bootShutdown";
  message.value = "";

  try {
    const result = await MiuiPower.openBootShutdownPage();
    message.value = result.ok
      ? `已尝试打开定时开关机页面，方式：${result.method}`
      : "无法打开 MIUI 定时开关机页面";
  } catch (error) {
    const maybeMessage =
      typeof error === "object" && error !== null && "message" in error
        ? String((error as { message?: string }).message ?? "")
        : "";
    message.value = maybeMessage || "无法打开 MIUI 定时开关机页面";
  } finally {
    loadingAction.value = "";
  }
}

async function openWirelessDebuggingPage() {
  loadingAction.value = "wirelessDebugging";
  message.value = "";

  try {
    const result = await MiuiPower.openWirelessDebuggingPage();
    message.value = result.ok
      ? `已尝试打开无线调试页面，方式：${result.method}`
      : "无法打开无线调试页面";
  } catch (error) {
    const maybeMessage =
      typeof error === "object" && error !== null && "message" in error
        ? String((error as { message?: string }).message ?? "")
        : "";
    message.value = maybeMessage || "无法打开无线调试页面";
  } finally {
    loadingAction.value = "";
  }
}

async function openDeveloperOptionsPage() {
  loadingAction.value = "developerOptions";
  message.value = "";

  try {
    const result = await MiuiPower.openDeveloperOptions();
    message.value = result.ok
      ? `已尝试打开开发者选项，方式：${result.method}`
      : "无法打开开发者选项";
  } catch (error) {
    const maybeMessage =
      typeof error === "object" && error !== null && "message" in error
        ? String((error as { message?: string }).message ?? "")
        : "";
    message.value = maybeMessage || "无法打开开发者选项";
  } finally {
    loadingAction.value = "";
  }
}

async function openScreenRefreshRatePage() {
  loadingAction.value = "screenRefreshRate";
  message.value = "";

  try {
    const result = await MiuiPower.openScreenRefreshRatePage();
    message.value = result.ok
      ? `已尝试打开屏幕刷新率设置，方式：${result.method}`
      : "无法打开屏幕刷新率设置";
  } catch (error) {
    const maybeMessage =
      typeof error === "object" && error !== null && "message" in error
        ? String((error as { message?: string }).message ?? "")
        : "";
    message.value = maybeMessage || "无法打开屏幕刷新率设置";
  } finally {
    loadingAction.value = "";
  }
}

async function openHonorOfKings() {
  loadingAction.value = "honorOfKings";
  message.value = "";

  try {
    const result = await MiuiPower.openHonorOfKings();
    if (result.ok) {
      message.value = "已尝试启动王者荣耀";
    } else if (!result.installed) {
      message.value = "未检测到王者荣耀（com.tencent.tmgp.sgame）已安装";
    } else {
      message.value = result.error || "无法启动王者荣耀";
    }
  } catch (error) {
    message.value = getErrorMessage(error) || "无法启动王者荣耀";
  } finally {
    loadingAction.value = "";
  }
}

async function startFocusOverlay() {
  loadingAction.value = "focusOverlay";
  message.value = "";

  try {
    const result = await MiuiPower.startFocusOverlay();
    if (result.overlayPermissionRequired || result.permissionRequired) {
      message.value = "需要先授予“显示在其他应用上层”权限，已尝试打开授权页。";
    } else if (result.ok && result.accessibilityPermissionRequired) {
      message.value =
        "悬浮按钮已启动，但未开启无障碍服务；已尝试打开无障碍设置。请开启后再读取焦点。";
    } else if (result.ok) {
      message.value = "已启动全局焦点悬浮按钮，点击按钮会读取并复制窗口焦点信息。";
    } else {
      message.value = result.error || "无法启动焦点悬浮按钮";
    }
  } catch (error) {
    message.value = getErrorMessage(error) || "无法启动焦点悬浮按钮";
  } finally {
    loadingAction.value = "";
  }
}

async function openAccessibilitySettings() {
  loadingAction.value = "accessibility";
  message.value = "";

  try {
    const result = await MiuiPower.openAccessibilitySettings();
    message.value = result.ok ? "已打开无障碍设置" : "无法打开无障碍设置";
  } catch (error) {
    message.value = getErrorMessage(error) || "无法打开无障碍设置";
  } finally {
    loadingAction.value = "";
  }
}

async function stopFocusOverlay() {
  message.value = "";

  try {
    const result = await MiuiPower.stopFocusOverlay();
    message.value = result.ok ? "已关闭焦点悬浮按钮" : "焦点悬浮按钮当前未运行";
  } catch (error) {
    message.value = getErrorMessage(error) || "无法关闭焦点悬浮按钮";
  }
}

async function getWindowFocusInfo() {
  loadingAction.value = "windowFocus";
  message.value = "";

  try {
    const result = await MiuiPower.getWindowFocusInfo();
    if (result.ok) {
      message.value = result.lines.join("\n");
    } else {
      const detail = result.lines.length > 0 ? `\n${result.lines.join("\n")}` : "";
      message.value = `${result.error || "读取窗口焦点失败"}${detail}`;
    }
  } catch (error) {
    message.value = getErrorMessage(error) || "读取窗口焦点失败";
  } finally {
    loadingAction.value = "";
  }
}

function getErrorMessage(error: unknown) {
  return typeof error === "object" && error !== null && "message" in error
    ? String((error as { message?: string }).message ?? "")
    : "";
}
</script>
