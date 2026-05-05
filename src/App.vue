<template>
  <main class="page" :data-theme="theme">
    <section class="card">
      <div class="heading">
        <h1>MIUI 系统页面 Launcher</h1>
        <button type="button" class="theme-toggle" @click="toggleTheme">
          {{ theme === "day" ? "切换到黑夜" : "切换到白天" }}
        </button>
      </div>
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

      <p v-if="message" class="message">{{ message }}</p>
    </section>
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
