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

        <button type="button" class="scene-pill" @click="toggleTheme">
          {{ theme === "day" ? "黑屏" : "白屏" }}
        </button>

        <button type="button" class="icon-circle" aria-label="说明">i</button>
        <button type="button" class="icon-circle" aria-label="主页">⌂</button>
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
  "" | "bootShutdown" | "wirelessDebugging" | "developerOptions"
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
</script>
