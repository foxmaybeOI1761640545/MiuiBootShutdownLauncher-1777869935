<template>
  <main class="page">
    <section class="card">
      <h1>MIUI 定时开关机 Launcher</h1>
      <p class="description">
        验证普通第三方应用是否可通过 Intent 打开 MIUI 系统“定时开关机”页面。
      </p>

      <button type="button" :disabled="loading" @click="openPage">
        {{ loading ? "正在打开..." : "打开定时开关机页面" }}
      </button>

      <p v-if="message" class="message">{{ message }}</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { MiuiPower } from "./plugins/miuiPower";

const loading = ref(false);
const message = ref("");

async function openPage() {
  loading.value = true;
  message.value = "";

  try {
    const result = await MiuiPower.openBootShutdownPage();
    message.value = result.ok
      ? `已尝试打开页面，方式：${result.method}`
      : "无法打开 MIUI 定时开关机页面";
  } catch (error) {
    const maybeMessage =
      typeof error === "object" && error !== null && "message" in error
        ? String((error as { message?: string }).message ?? "")
        : "";
    message.value = maybeMessage || "无法打开 MIUI 定时开关机页面";
  } finally {
    loading.value = false;
  }
}
</script>
