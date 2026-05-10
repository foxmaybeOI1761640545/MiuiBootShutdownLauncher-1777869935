<template>
  <main class="page" :class="themeClass">
    <section class="launcher-shell">
      <section class="zone1-fixed">
        <header class="app-header">
          <h1>Launcher</h1>
        </header>

        <article class="search-panel search-panel--fixed" data-locate="search-global-fixed">
          <label class="search-label" for="global-search-fixed">Global Search</label>
          <input
            id="global-search-fixed"
            v-model.trim="searchKeyword"
            type="search"
            class="search-input"
            placeholder="Search button/group/setting"
          />
          <ul v-if="searchKeyword && searchResults.length > 0" class="search-results">
            <li v-for="item in searchResults" :key="item.id">
              <button type="button" class="search-result-btn" @click="locateFromSearch(item)">
                <span>{{ item.label }}</span>
                <small>{{ item.hint }}</small>
              </button>
            </li>
          </ul>
          <p v-else-if="searchKeyword" class="search-empty">No matched item.</p>
        </article>
      </section>

      <section
        class="content-viewport"
        @touchstart.capture.passive="onContentTouchStart"
        @touchmove.capture.passive="onContentTouchMove"
        @touchend.capture.passive="onContentTouchEnd"
        @touchcancel.capture.passive="onContentTouchCancel"
        @click.capture="onContentClickCapture"
      >
        <article
          ref="pageScrollRef"
          class="zone2-panel page-scroll"
          :data-page-id="activePage"
          @scroll.passive="onPageScroll"
        >
          <template v-if="activePage !== 'settings'">
            <div v-if="runtimeGroupsForPage(activePage).length === 0" class="search-empty">
                  No group available.
            </div>
            <section
              v-for="group in runtimeGroupsForPage(activePage)"
              :key="group.id"
              class="zone2-group"
              :data-locate="`group-${activePage}-${group.id}`"
              :class="highlightClass(`group-${activePage}-${group.id}`)"
            >
              <button type="button" class="group-header" @click="toggleRuntimeGroup(activePage, group.id)">
                <span>{{ group.title }}</span>
                <span>{{ isRuntimeGroupCollapsed(activePage, group.id) ? "Expand" : "Collapse" }}</span>
              </button>
              <div v-if="!isRuntimeGroupCollapsed(activePage, group.id)" class="extra-grid">
                <button
                  v-for="entry in zone2EntriesByGroup(activePage, group.id)"
                  :key="entry.id"
                  type="button"
                  class="action-btn action-btn--compact"
                  :class="[
                    spanClass(entry.size === 'large' ? 'full' : 'half'),
                    variantClass(entry.variant),
                    highlightClass(entry.locateKey),
                    { 'is-disabled': entry.disabled },
                  ]"
                  :data-locate="entry.locateKey"
                  :disabled="isLoading || entry.disabled"
                  @click="runZone2Entry(entry)"
                >
                  {{ zone2EntryLabel(entry) }}
                </button>
              </div>
            </section>
          </template>

          <template v-else>
            <section
              v-for="group in settingsGroups"
              :key="group.id"
              class="zone2-group settings-group"
              :data-locate="group.locateKey"
              :class="highlightClass(group.locateKey)"
            >
              <button type="button" class="group-header" @click="toggleSettingsGroup(group.id)">
                <span>{{ group.title }}</span>
                <span>{{ isSettingsGroupCollapsed(group.id) ? "Expand" : "Collapse" }}</span>
              </button>
              <div v-if="!isSettingsGroupCollapsed(group.id)" class="settings-group-content">
                    <template v-if="group.id === 'settings-save'">
                      <p class="settings-help">
                        Only config-driven content is editable. Save applies changes, revert restores last saved state.
                      </p>
                      <div class="settings-actions">
                        <button
                          type="button"
                          class="action-btn action-btn--compact tone-beige"
                          :disabled="!isDraftDirty"
                          @click="saveDraftConfig"
                        >
                          Save Config
                        </button>
                        <button
                          type="button"
                          class="action-btn action-btn--compact tone-pink"
                          :disabled="!isDraftDirty"
                          @click="revertDraftConfig"
                        >
                          Revert Changes
                        </button>
                      </div>
                    </template>

                    <template v-else-if="group.id === 'settings-startup'">
                      <div class="settings-grid">
                        <label>
                          Startup Mode
                          <select v-model="draftConfig.startupPolicy.mode">
                            <option value="fixed">Fixed Page</option>
                            <option value="remember">Remember Last Page</option>
                          </select>
                        </label>
                        <label>
                          Fixed Page
                          <select
                            v-model="draftConfig.startupPolicy.fixedPageId"
                            :disabled="draftConfig.startupPolicy.mode !== 'fixed'"
                          >
                            <option value="page1">Page 1</option>
                            <option value="page2">Page 2</option>
                            <option value="settings">Settings</option>
                          </select>
                        </label>
                        <label>
                          Theme
                          <select v-model="themeMode">
                            <option value="light">Light</option>
                            <option value="dark">Dark</option>
                          </select>
                        </label>
                      </div>
                      <p class="settings-help">
                        Startup policy is saved manually. Last active page and collapsed state are auto-saved.
                      </p>
                      <p class="settings-help">Theme mode is runtime-only and is not persisted.</p>
                    </template>

                    <template v-else-if="group.id === 'settings-actions'">
                      <div class="settings-grid">
                        <div v-for="row in draftPlacementRows" :key="row.id" class="settings-row">
                          <div class="settings-row-title">
                            <strong>{{ row.label }}</strong>
                            <small>{{ row.sourceLabel }}</small>
                          </div>
                          <label>
                            Page
                            <select :value="row.placement.pageId" @change="updateRowPage(row, $event)">
                              <option value="page1">Page 1</option>
                              <option value="page2">Page 2</option>
                            </select>
                          </label>
                          <label>
                            Group
                            <select :value="row.placement.groupId" @change="updateRowGroup(row, $event)">
                              <option
                                v-for="runtimeGroup in groupsForPage(row.placement.pageId)"
                                :key="runtimeGroup.id"
                                :value="runtimeGroup.id"
                              >
                                {{ runtimeGroup.title }}
                              </option>
                            </select>
                          </label>
                          <label>
                            Size
                            <select :value="row.placement.size" @change="updateRowSize(row, $event)">
                              <option value="small">Small</option>
                              <option value="large">Large</option>
                            </select>
                          </label>
                          <div class="settings-row-btns">
                            <button type="button" class="mini-btn" @click="moveRow(row, -1)">Up</button>
                            <button type="button" class="mini-btn" @click="moveRow(row, 1)">Down</button>
                          </div>
                        </div>
                      </div>
                    </template>

                    <template v-else-if="group.id === 'settings-groups'">
                      <div class="group-create">
                        <label>
                          Page
                          <select v-model="newGroupForm.pageId">
                            <option value="page1">Page 1</option>
                            <option value="page2">Page 2</option>
                          </select>
                        </label>
                        <label>
                          Title
                          <input v-model.trim="newGroupForm.title" type="text" placeholder="e.g. System Tools" />
                        </label>
                        <label class="checkbox-label">
                          <input v-model="newGroupForm.collapsedByDefault" type="checkbox" />
                          Collapsed by default
                        </label>
                        <button type="button" class="mini-btn" @click="createGroup">Create Group</button>
                      </div>

                      <div class="settings-grid">
                        <div v-for="runtimeGroup in draftGroups" :key="runtimeGroup.id" class="settings-row">
                          <div class="settings-row-title">
                            <strong>{{ runtimeGroup.id }}</strong>
                            <small>{{ runtimeGroup.pageId === "page1" ? "Page 1" : "Page 2" }}</small>
                          </div>
                          <label>
                            Title
                            <input v-model.trim="runtimeGroup.title" type="text" />
                          </label>
                          <label class="checkbox-label">
                            <input v-model="runtimeGroup.collapsedByDefault" type="checkbox" />
                            Collapsed by default
                          </label>
                          <div class="settings-row-btns">
                            <button type="button" class="mini-btn" @click="moveGroup(runtimeGroup, -1)">Up</button>
                            <button type="button" class="mini-btn" @click="moveGroup(runtimeGroup, 1)">Down</button>
                            <button
                              type="button"
                              class="mini-btn mini-btn-danger"
                              :disabled="groupHasRows(runtimeGroup)"
                              @click="deleteGroup(runtimeGroup)"
                            >
                              Delete
                            </button>
                          </div>
                        </div>
                      </div>
                      <p class="settings-help">Groups with entries cannot be deleted until entries are moved out.</p>
                    </template>

                    <template v-else-if="group.id === 'settings-custom'">
                      <p class="settings-help">
                        Experimental custom actions support builtin reference or custom Intent/package/activity/action/url.
                      </p>

                      <div class="custom-toolbar">
                        <button type="button" class="mini-btn" @click="startCreateCustom">Create Custom Action</button>
                      </div>

                      <div v-if="draftCustomActions.length > 0" class="custom-list">
                        <div v-for="item in draftCustomActions" :key="item.id" class="custom-item">
                          <div>
                            <strong>{{ item.label }}</strong>
                            <small>{{ item.id }}</small>
                          </div>
                          <div class="settings-row-btns">
                            <button type="button" class="mini-btn" @click="editCustom(item.id)">Edit</button>
                            <button type="button" class="mini-btn" @click="testCustom(item)">Test</button>
                            <button type="button" class="mini-btn mini-btn-danger" @click="removeCustom(item.id)">
                              Delete
                            </button>
                          </div>
                        </div>
                      </div>

                      <div class="custom-editor">
                        <h3>{{ editingCustomId ? "Edit Custom Action" : "Create Custom Action" }}</h3>
                        <div class="settings-grid">
                          <label>
                            Name
                            <input v-model.trim="customForm.label" type="text" placeholder="Action name" />
                          </label>
                          <label>
                            Loading Label
                            <input
                              v-model.trim="customForm.loadingLabel"
                              type="text"
                              placeholder="Running..."
                            />
                          </label>
                          <label>
                            Variant
                            <select v-model="customForm.variant">
                              <option value="pink">Pink</option>
                              <option value="beige">Beige</option>
                            </select>
                          </label>
                          <label>
                            Search Aliases
                            <input v-model.trim="customForm.searchAliasesText" type="text" placeholder="alias1, alias2" />
                          </label>
                          <label>
                            Page
                            <select v-model="customForm.pageId" @change="ensureCustomFormGroup">
                              <option value="page1">Page 1</option>
                              <option value="page2">Page 2</option>
                            </select>
                          </label>
                          <label>
                            Group
                            <select v-model="customForm.groupId">
                              <option
                                v-for="runtimeGroup in groupsForPage(customForm.pageId)"
                                :key="runtimeGroup.id"
                                :value="runtimeGroup.id"
                              >
                                {{ runtimeGroup.title }}
                              </option>
                            </select>
                          </label>
                          <label>
                            Size
                            <select v-model="customForm.size">
                              <option value="small">Small</option>
                              <option value="large">Large</option>
                            </select>
                          </label>
                          <label>
                            Executor
                            <select v-model="customForm.executorKind">
                              <option value="builtin_ref">Builtin Reference</option>
                              <option value="custom_intent">Custom Intent</option>
                            </select>
                          </label>
                        </div>

                        <div v-if="customForm.executorKind === 'builtin_ref'" class="settings-grid">
                          <label>
                            Builtin Action
                            <select v-model="customForm.builtinActionKey">
                              <option v-for="option in editableBuiltinOptions" :key="option.key" :value="option.key">
                                {{ option.label }}
                              </option>
                            </select>
                          </label>
                        </div>

                        <div v-else class="settings-grid">
                          <label>
                            Action
                            <input
                              v-model.trim="customForm.action"
                              type="text"
                              placeholder="android.intent.action.VIEW"
                            />
                          </label>
                          <label>
                            Package
                            <input v-model.trim="customForm.packageName" type="text" placeholder="com.example.app" />
                          </label>
                          <label>
                            Activity
                            <input
                              v-model.trim="customForm.className"
                              type="text"
                              placeholder="com.example.app.MainActivity"
                            />
                          </label>
                          <label>
                            URL
                            <input v-model.trim="customForm.url" type="text" placeholder="https://example.com" />
                          </label>
                          <label>
                            Data URI
                            <input v-model.trim="customForm.dataUri" type="text" placeholder="content://..." />
                          </label>
                          <label>
                            Mime Type
                            <input v-model.trim="customForm.mimeType" type="text" placeholder="text/plain" />
                          </label>
                          <label>
                            Categories (comma separated)
                            <input
                              v-model.trim="customForm.categoriesText"
                              type="text"
                              placeholder="android.intent.category.DEFAULT"
                            />
                          </label>
                          <label>
                            Extras (JSON object)
                            <textarea
                              v-model.trim="customForm.extrasJson"
                              rows="4"
                              placeholder='{"source":"launcher","debug":true}'
                            />
                          </label>
                        </div>

                        <div class="settings-row-btns">
                          <button type="button" class="mini-btn" @click="saveCustomForm">Save Custom Action</button>
                          <button type="button" class="mini-btn" @click="testCustomForm">Test Current Config</button>
                        </div>
                      </div>
                    </template>
                  </div>
                </section>
              </template>
            </article>
          </section>

      <section class="bottom-fixed">
        <section class="status-panel" aria-live="polite">
          <p class="status-label">Recent Action</p>
          <p class="status-message">{{ displayMessage }}</p>
        </section>

        <nav class="bottom-nav" aria-label="Page Navigation">
          <button
            v-for="item in navItems"
            :key="item.id"
            type="button"
            class="nav-btn"
            :class="{ active: activePage === item.id }"
            @click="switchPage(item.id)"
          >
            <span class="nav-btn-bg">
              <img :src="item.icon" :alt="item.label" class="nav-icon" />
            </span>
          </button>
        </nav>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from "vue";
import {
  type LaunchIntentOptions,
  type OpenAppCommandResult,
  type LaunchIntentExtraValue,
} from "./plugins/miuiPower";
import { MiuiPower } from "./plugins/miuiPower";
import {
  buildDefaultLauncherConfig,
  buildDefaultLauncherUiState,
  cloneLauncherConfig,
  type CustomAction,
  type CustomIntentExecutor,
  LAUNCHER_CONFIG_VERSION,
  loadLauncherConfig,
  loadLauncherUiState,
  normalizeLauncherConfig,
  type LauncherConfigV3,
  type LauncherUiState,
  type RuntimePageId as LauncherRuntimePageId,
  saveLauncherConfig,
  saveLauncherUiState,
  type Zone2Group,
  type Zone2Placement,
  type Zone2Size,
  type ZonePageId,
} from "./launcherConfig";
import page1Icon from "./assets/nav-page-1.svg";
import page2Icon from "./assets/nav-page-2.svg";
import page3Icon from "./assets/nav-page-3.svg";

type RuntimePageId = LauncherRuntimePageId;
type ActionVariant = "pink" | "beige";
type CellSpan = "half" | "full";
type ThemeMode = "light" | "dark";

interface ActionItem {
  key: string;
  label: string;
  loadingLabel: string;
  variant: ActionVariant;
  run?: () => Promise<void>;
  disabled?: boolean;
}

interface NavItem {
  id: RuntimePageId;
  label: string;
  icon: string;
}

interface RuntimeZone2Entry {
  id: string;
  source: "builtin" | "custom";
  label: string;
  loadingLabel: string;
  variant: ActionVariant;
  size: Zone2Size;
  pageId: ZonePageId;
  groupId: string;
  order: number;
  locateKey: string;
  searchAliases: string[];
  disabled: boolean;
  builtinKey?: string;
  custom?: CustomAction;
}

interface SearchResultItem {
  id: string;
  pageId: RuntimePageId;
  locateKey: string;
  label: string;
  hint: string;
  keywords: string[];
  groupId?: string;
  groupScope?: "runtime" | "settings";
}

interface DraftPlacementRow {
  id: string;
  source: "builtin" | "custom";
  label: string;
  sourceLabel: string;
  placement: Zone2Placement;
  builtinKey?: string;
  customId?: string;
}

interface CustomFormState {
  label: string;
  loadingLabel: string;
  variant: ActionVariant;
  searchAliasesText: string;
  pageId: ZonePageId;
  groupId: string;
  size: Zone2Size;
  executorKind: "builtin_ref" | "custom_intent";
  builtinActionKey: string;
  action: string;
  packageName: string;
  className: string;
  url: string;
  dataUri: string;
  mimeType: string;
  categoriesText: string;
  extrasJson: string;
}

interface SettingsGroupDefinition {
  id: "settings-save" | "settings-startup" | "settings-actions" | "settings-groups" | "settings-custom";
  title: string;
  locateKey: string;
  collapsedByDefault: boolean;
}

const NO_SWIPE_SELECTOR =
  'input, textarea, select, option, [contenteditable="true"], .no-swipe, [data-no-swipe="true"], [data-scroll-lock="true"], [data-drag-handle="true"]';

function resolveInitialThemeMode(): ThemeMode {
  if (typeof window !== "undefined" && typeof window.matchMedia === "function") {
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  return "light";
}

const activePage = ref<RuntimePageId>("page1");
const pageScrollRef = ref<HTMLElement | null>(null);
const isSwitchingPage = ref(false);
const themeMode = ref<ThemeMode>(resolveInitialThemeMode());
const themeClass = computed(() => (themeMode.value === "dark" ? "theme-dark" : "theme-light"));
const uiState = ref<LauncherUiState>(loadLauncherUiState(buildDefaultLauncherUiState()));
const searchKeyword = ref("");
const pageScrollTopByPage = ref<Record<RuntimePageId, number>>({
  page1: 0,
  page2: 0,
  settings: 0,
});
let touchStartX = 0;
let touchStartY = 0;
let touchStartTime = 0;
let touchIdentifier: number | null = null;
let swipeLocked = false;
let swipeAxis: "horizontal" | "vertical" | null = null;
let swipeSuppressClickUntil = 0;

const loadingAction = ref("");
const message = ref("");

const isLoading = computed(() => loadingAction.value !== "");
const displayMessage = computed(() => message.value || "Waiting for action. Tap any entry button.");

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

async function runBuiltinAction(key: string) {
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

const editableBuiltinKeys = [
  "bootShutdown",
  "developerOptions",
  "accessibility",
  "screenRefreshRate",
  "wirelessDebugging",
  "focusOverlay",
  "stopFocusOverlay",
  "honorOfKings",
  "openBilibili",
  "openWeChat",
  "qqPlaceholder",
  "openQQMusic",
  "openCamera",
  "openGallery",
  "windowFocus",
  "screenTimePage",
  "usageAccessSettings",
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
  "openWeather",
  "openRecorder",
  "recordSound",
  "openAuthenticator",
  "takePhoto",
  "takeVideo",
  "pickImage",
  "pickVideo",
] as const;

const editableBuiltinKeySet = new Set<string>(editableBuiltinKeys);

const defaultGroups: Zone2Group[] = [
  { id: "p1-common", pageId: "page1", title: "常用入口", order: 0, collapsedByDefault: false },
  { id: "p1-system", pageId: "page1", title: "系统与权限", order: 1, collapsedByDefault: false },
  { id: "p1-device", pageId: "page1", title: "设备工具", order: 2, collapsedByDefault: false },
  { id: "p2-common", pageId: "page2", title: "常用入口", order: 0, collapsedByDefault: false },
  { id: "p2-files", pageId: "page2", title: "文件与浏览器", order: 1, collapsedByDefault: false },
  { id: "p2-map", pageId: "page2", title: "地图与定位", order: 2, collapsedByDefault: true },
  { id: "p2-share", pageId: "page2", title: "分享与链接", order: 3, collapsedByDefault: true },
  { id: "p2-media", pageId: "page2", title: "影音与拍摄", order: 4, collapsedByDefault: false },
  { id: "p2-labs", pageId: "page2", title: "实验入口", order: 5, collapsedByDefault: true },
];

function seed(
  actionKey: string,
  pageId: ZonePageId,
  groupId: string,
  order: number,
  size: Zone2Size = "small",
  searchAliases: string[] = [],
) {
  return {
    actionKey,
    label: actionByKey[actionKey]?.label ?? actionKey,
    searchAliases,
    placement: {
      pageId,
      groupId,
      order,
      size,
    },
  };
}

const defaultLauncherConfig = buildDefaultLauncherConfig({
  groups: defaultGroups,
  builtinActions: [
    seed("bootShutdown", "page1", "p1-common", 0),
    seed("developerOptions", "page1", "p1-common", 1),
    seed("accessibility", "page1", "p1-common", 2),
    seed("screenRefreshRate", "page1", "p1-common", 3),
    seed("wirelessDebugging", "page1", "p1-common", 4, "large"),
    seed("focusOverlay", "page1", "p1-common", 5),
    seed("stopFocusOverlay", "page1", "p1-common", 6),
    seed("honorOfKings", "page2", "p2-common", 0),
    seed("openBilibili", "page2", "p2-common", 1),
    seed("openWeChat", "page2", "p2-common", 2),
    seed("qqPlaceholder", "page2", "p2-common", 3),
    seed("openQQMusic", "page2", "p2-common", 4, "large"),
    seed("openCamera", "page2", "p2-common", 5),
    seed("openGallery", "page2", "p2-common", 6),
    seed("windowFocus", "page1", "p1-system", 0, "small", ["焦点", "窗口"]),
    seed("screenTimePage", "page1", "p1-system", 1),
    seed("usageAccessSettings", "page1", "p1-system", 2),
    seed("openWeather", "page1", "p1-device", 0),
    seed("openRecorder", "page1", "p1-device", 1),
    seed("recordSound", "page1", "p1-device", 2),
    seed("openAuthenticator", "page1", "p1-device", 3),
    seed("openFileManager", "page2", "p2-files", 0),
    seed("pickFile", "page2", "p2-files", 1),
    seed("pickFolder", "page2", "p2-files", 2),
    seed("openChrome", "page2", "p2-files", 3),
    seed("openUrl", "page2", "p2-files", 4),
    seed("openChromeIncognito", "page2", "p2-files", 5),
    seed("openAmap", "page2", "p2-map", 0),
    seed("openMapLocation", "page2", "p2-map", 1),
    seed("openNavigation", "page2", "p2-map", 2),
    seed("openMapSearch", "page2", "p2-map", 3),
    seed("shareText", "page2", "p2-share", 0),
    seed("shareUrl", "page2", "p2-share", 1),
    seed("openMusicLink", "page2", "p2-share", 2),
    seed("openBiliUrl", "page2", "p2-share", 3),
    seed("takePhoto", "page2", "p2-media", 0),
    seed("takeVideo", "page2", "p2-media", 1),
    seed("pickImage", "page2", "p2-media", 2),
    seed("pickVideo", "page2", "p2-media", 3),
    seed("openKeep", "page2", "p2-labs", 0),
    seed("openClash", "page2", "p2-labs", 1),
    seed("openDoubao", "page2", "p2-labs", 2),
    seed("shareToDoubao", "page2", "p2-labs", 3),
  ],
});

const savedConfig = ref<LauncherConfigV3>(
  loadLauncherConfig(defaultLauncherConfig, editableBuiltinKeySet),
);
const draftConfig = ref<LauncherConfigV3>(cloneLauncherConfig(savedConfig.value));
const isDraftDirty = computed(
  () => JSON.stringify(draftConfig.value) !== JSON.stringify(savedConfig.value),
);

const navItems: NavItem[] = [
  { id: "page1", label: "Page 1", icon: page1Icon },
  { id: "page2", label: "Page 2", icon: page2Icon },
  { id: "settings", label: "Settings", icon: page3Icon },
];

const editableBuiltinOptions = computed(() =>
  editableBuiltinKeys.map((key) => ({
    key,
    label: actionByKey[key]?.label ?? key,
  })),
);

const collapsedGroupMap = ref<Record<string, boolean>>({});
const highlightedLocateKey = ref("");
let highlightTimer: number | null = null;
const settingsGroups: SettingsGroupDefinition[] = [
  { id: "settings-save", title: "保存与撤销", locateKey: "settings-save", collapsedByDefault: false },
  { id: "settings-startup", title: "启动页策略", locateKey: "settings-startup", collapsedByDefault: false },
  { id: "settings-actions", title: "按钮分配", locateKey: "settings-actions", collapsedByDefault: false },
  { id: "settings-groups", title: "分组管理", locateKey: "settings-groups", collapsedByDefault: true },
  { id: "settings-custom", title: "自定义按钮", locateKey: "settings-custom", collapsedByDefault: true },
];
const pageOrder: RuntimePageId[] = ["page1", "page2", "settings"];

function resolveStartupPage(config: LauncherConfigV3, state: LauncherUiState): RuntimePageId {
  return config.startupPolicy.mode === "remember" ? state.lastActivePage : config.startupPolicy.fixedPageId;
}

function runtimeGroupStateKey(pageId: ZonePageId, groupId: string) {
  return `runtime|${pageId}|${groupId}`;
}

function settingsGroupStateKey(groupId: string) {
  return `settings|${groupId}`;
}

activePage.value = resolveStartupPage(savedConfig.value, uiState.value);
collapsedGroupMap.value = { ...uiState.value.collapsedGroups };

function persistUiState() {
  const nextState: LauncherUiState = {
    version: uiState.value.version,
    lastActivePage: activePage.value,
    collapsedGroups: { ...collapsedGroupMap.value },
  };
  uiState.value = nextState;
  saveLauncherUiState(nextState);
}

function highlightClass(locateKey: string) {
  return highlightedLocateKey.value === locateKey ? "is-highlight" : "";
}

function markHighlight(locateKey: string) {
  highlightedLocateKey.value = locateKey;
  if (highlightTimer !== null) {
    window.clearTimeout(highlightTimer);
  }
  highlightTimer = window.setTimeout(() => {
    if (highlightedLocateKey.value === locateKey) {
      highlightedLocateKey.value = "";
    }
  }, 2200);
}

function syncCollapsedGroupMap() {
  const next: Record<string, boolean> = {};
  for (const group of savedConfig.value.groups) {
    const key = runtimeGroupStateKey(group.pageId, group.id);
    next[key] = collapsedGroupMap.value[key] ?? group.collapsedByDefault;
  }
  for (const group of settingsGroups) {
    const key = settingsGroupStateKey(group.id);
    next[key] = collapsedGroupMap.value[key] ?? group.collapsedByDefault;
  }
  collapsedGroupMap.value = next;
}

watch(
  () => savedConfig.value.groups,
  () => {
    syncCollapsedGroupMap();
  },
  { deep: true, immediate: true },
);

watch(activePage, () => {
  persistUiState();
});

watch(
  collapsedGroupMap,
  () => {
    persistUiState();
  },
  { deep: true },
);

function toggleRuntimeGroup(pageId: RuntimePageId, groupId: string) {
  if (pageId === "settings") {
    return;
  }
  const key = runtimeGroupStateKey(pageId, groupId);
  const previous = collapsedGroupMap.value[key] ?? false;
  collapsedGroupMap.value = {
    ...collapsedGroupMap.value,
    [key]: !previous,
  };
}

function setRuntimeGroupCollapsed(pageId: RuntimePageId, groupId: string, collapsed: boolean) {
  if (pageId === "settings") {
    return;
  }
  collapsedGroupMap.value = {
    ...collapsedGroupMap.value,
    [runtimeGroupStateKey(pageId, groupId)]: collapsed,
  };
}

function isRuntimeGroupCollapsed(pageId: RuntimePageId, groupId: string) {
  if (pageId === "settings") {
    return false;
  }
  return collapsedGroupMap.value[runtimeGroupStateKey(pageId, groupId)] ?? false;
}

function toggleSettingsGroup(groupId: SettingsGroupDefinition["id"]) {
  const key = settingsGroupStateKey(groupId);
  const previous = collapsedGroupMap.value[key] ?? false;
  collapsedGroupMap.value = {
    ...collapsedGroupMap.value,
    [key]: !previous,
  };
}

function setSettingsGroupCollapsed(groupId: SettingsGroupDefinition["id"], collapsed: boolean) {
  collapsedGroupMap.value = {
    ...collapsedGroupMap.value,
    [settingsGroupStateKey(groupId)]: collapsed,
  };
}

function isSettingsGroupCollapsed(groupId: SettingsGroupDefinition["id"]) {
  return collapsedGroupMap.value[settingsGroupStateKey(groupId)] ?? false;
}

function isSettingsGroupId(value: string): value is SettingsGroupDefinition["id"] {
  return settingsGroups.some((group) => group.id === value);
}

function resetTouchState() {
  touchStartX = 0;
  touchStartY = 0;
  touchStartTime = 0;
  touchIdentifier = null;
  swipeLocked = false;
  swipeAxis = null;
}

function getNextPage(pageId: RuntimePageId): RuntimePageId {
  const index = pageOrder.indexOf(pageId);
  if (index < 0) {
    return pageOrder[0];
  }
  return pageOrder[(index + 1) % pageOrder.length];
}

function getPrevPage(pageId: RuntimePageId): RuntimePageId {
  const index = pageOrder.indexOf(pageId);
  if (index < 0) {
    return pageOrder[0];
  }
  return pageOrder[(index - 1 + pageOrder.length) % pageOrder.length];
}

function shouldIgnoreSwipe(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  return Boolean(target.closest(NO_SWIPE_SELECTOR));
}

function shouldSuppressClickAfterSwipe() {
  return Date.now() < swipeSuppressClickUntil;
}

function onContentClickCapture(event: MouseEvent) {
  if (!shouldSuppressClickAfterSwipe()) {
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  event.stopImmediatePropagation?.();
}

function findTrackedTouch(touchList: TouchList) {
  if (touchIdentifier === null) {
    return null;
  }
  for (let index = 0; index < touchList.length; index += 1) {
    const touch = touchList.item(index);
    if (touch && touch.identifier === touchIdentifier) {
      return touch;
    }
  }
  return null;
}

function onContentTouchStart(event: TouchEvent) {
  resetTouchState();
  if (isSwitchingPage.value) {
    swipeLocked = true;
    return;
  }
  if (event.touches.length !== 1) {
    swipeLocked = true;
    return;
  }
  if (shouldIgnoreSwipe(event.target)) {
    swipeLocked = true;
    return;
  }

  const touch = event.touches[0];
  touchStartX = touch.clientX;
  touchStartY = touch.clientY;
  touchStartTime = Date.now();
  touchIdentifier = touch.identifier;
}

function onContentTouchMove(event: TouchEvent) {
  if (swipeLocked) {
    return;
  }
  if (event.touches.length !== 1) {
    swipeLocked = true;
    return;
  }
  const touch = findTrackedTouch(event.touches);
  if (!touch) {
    swipeLocked = true;
    return;
  }

  const deltaX = touch.clientX - touchStartX;
  const deltaY = touch.clientY - touchStartY;
  const absX = Math.abs(deltaX);
  const absY = Math.abs(deltaY);
  if (absX < 8 && absY < 8) {
    return;
  }
  if (absY > absX) {
    swipeAxis = "vertical";
    swipeLocked = true;
    return;
  }
  if (absX > absY * 1.5) {
    swipeAxis = "horizontal";
  }
}

async function onContentTouchEnd(event: TouchEvent) {
  try {
    if (swipeLocked || swipeAxis !== "horizontal" || touchIdentifier === null || isSwitchingPage.value) {
      return;
    }

    const touch = findTrackedTouch(event.changedTouches);
    if (!touch) {
      return;
    }

    const deltaX = touch.clientX - touchStartX;
    const deltaY = touch.clientY - touchStartY;
    const duration = Date.now() - touchStartTime;
    const absX = Math.abs(deltaX);
    const absY = Math.abs(deltaY);
    const isHorizontalSwipe = absX > 50 && absX > absY * 1.5 && duration < 600;
    if (!isHorizontalSwipe) {
      return;
    }

    swipeSuppressClickUntil = Date.now() + 250;
    const targetPage = deltaX < 0 ? getNextPage(activePage.value) : getPrevPage(activePage.value);
    await switchPage(targetPage);
  } finally {
    resetTouchState();
  }
}

function onContentTouchCancel(event: TouchEvent) {
  try {
    if (event.touches.length > 1 || event.changedTouches.length > 1) {
      swipeLocked = true;
    }
  } finally {
    resetTouchState();
  }
}

function capturePageScroll(pageId: RuntimePageId) {
  const container = pageScrollRef.value;
  if (!container) {
    return;
  }
  pageScrollTopByPage.value[pageId] = container.scrollTop;
}

function restorePageScroll(pageId: RuntimePageId) {
  const container = pageScrollRef.value;
  if (!container) {
    return;
  }
  container.scrollTop = pageScrollTopByPage.value[pageId] ?? 0;
}

function onPageScroll() {
  const container = pageScrollRef.value;
  if (!container) {
    return;
  }
  pageScrollTopByPage.value[activePage.value] = container.scrollTop;
}

async function switchPage(pageId: RuntimePageId) {
  if (pageId === activePage.value || isSwitchingPage.value) {
    return;
  }
  isSwitchingPage.value = true;
  try {
    capturePageScroll(activePage.value);
    activePage.value = pageId;
    await nextTick();
    restorePageScroll(pageId);
  } finally {
    isSwitchingPage.value = false;
  }
}

onBeforeUnmount(() => {
  if (highlightTimer !== null) {
    window.clearTimeout(highlightTimer);
    highlightTimer = null;
  }
});

const runtimeGroupsByPage = computed<Record<ZonePageId, Zone2Group[]>>(() => ({
  page1: savedConfig.value.groups
    .filter((group) => group.pageId === "page1")
    .sort((left, right) => left.order - right.order || left.title.localeCompare(right.title)),
  page2: savedConfig.value.groups
    .filter((group) => group.pageId === "page2")
    .sort((left, right) => left.order - right.order || left.title.localeCompare(right.title)),
}));

function runtimeGroupsForPage(pageId: RuntimePageId): Zone2Group[] {
  if (pageId === "settings") {
    return [];
  }
  return runtimeGroupsByPage.value[pageId];
}

function normalizeUrlLikeBrowser(raw: string | undefined) {
  const value = (raw ?? "").trim();
  if (!value) {
    return "";
  }
  return value.includes("://") ? value : `https://${value}`;
}

function isCustomActionDisabled(action: CustomAction) {
  if (action.executor.kind === "builtin_ref") {
    return isActionDisabled(action.executor.actionKey);
  }

  const hasTarget =
    Boolean(action.executor.action?.trim()) ||
    Boolean(action.executor.packageName?.trim()) ||
    Boolean(action.executor.className?.trim()) ||
    Boolean(action.executor.dataUri?.trim()) ||
    Boolean(action.executor.url?.trim());

  if (!hasTarget) {
    return true;
  }

  return Boolean(action.executor.className && !action.executor.packageName);
}

const runtimeZone2Entries = computed<RuntimeZone2Entry[]>(() => {
  const output: RuntimeZone2Entry[] = [];

  for (const item of savedConfig.value.builtinActions) {
    const action = actionByKey[item.actionKey];
    if (!action) {
      continue;
    }
    output.push({
      id: item.id,
      source: "builtin",
      label: action.label,
      loadingLabel: action.loadingLabel,
      variant: action.variant,
      size: item.placement.size,
      pageId: item.placement.pageId,
      groupId: item.placement.groupId,
      order: item.placement.order,
      locateKey: `zone2-${item.placement.pageId}-${item.placement.groupId}-${item.actionKey}`,
      searchAliases: item.searchAliases,
      disabled: isActionDisabled(item.actionKey),
      builtinKey: item.actionKey,
    });
  }

  for (const item of savedConfig.value.customActions) {
    output.push({
      id: item.id,
      source: "custom",
      label: item.label,
      loadingLabel: item.loadingLabel,
      variant: item.variant,
      size: item.placement.size,
      pageId: item.placement.pageId,
      groupId: item.placement.groupId,
      order: item.placement.order,
      locateKey: `zone2-${item.placement.pageId}-${item.placement.groupId}-${item.id}`,
      searchAliases: item.searchAliases,
      disabled: isCustomActionDisabled(item),
      custom: item,
    });
  }

  return output.sort((left, right) => {
    if (left.pageId !== right.pageId) {
      return left.pageId.localeCompare(right.pageId);
    }
    if (left.groupId !== right.groupId) {
      return left.groupId.localeCompare(right.groupId);
    }
    if (left.order !== right.order) {
      return left.order - right.order;
    }
    return left.label.localeCompare(right.label);
  });
});

const runtimeZone2EntryMap = computed(() => {
  const map = new Map<string, RuntimeZone2Entry[]>();
  for (const entry of runtimeZone2Entries.value) {
    const key = runtimeGroupStateKey(entry.pageId, entry.groupId);
    const rows = map.get(key) ?? [];
    rows.push(entry);
    map.set(key, rows);
  }
  for (const rows of map.values()) {
    rows.sort((left, right) => left.order - right.order || left.label.localeCompare(right.label));
  }
  return map;
});

function zone2EntriesByGroup(pageId: ZonePageId, groupId: string) {
  return runtimeZone2EntryMap.value.get(runtimeGroupStateKey(pageId, groupId)) ?? [];
}

function zone2EntryLabel(entry: RuntimeZone2Entry) {
  if (entry.source === "builtin" && entry.builtinKey) {
    return buttonLabel(entry.builtinKey);
  }
  const custom = entry.custom;
  if (!custom) {
    return entry.label;
  }
  return loadingAction.value === `custom:${custom.id}` ? custom.loadingLabel : custom.label;
}

async function runCustomAction(custom: CustomAction, testing = false) {
  if (custom.executor.kind === "builtin_ref") {
    await runBuiltinAction(custom.executor.actionKey);
    return;
  }

  const label = testing ? `${custom.label}（测试）` : custom.label;
  const loadingKey = `custom:${custom.id}`;
  await runWithLoading(loadingKey, `${label}执行失败`, async () => {
    const executor = custom.executor as CustomIntentExecutor;
    const normalizedUrl = normalizeUrlLikeBrowser(executor.url);
    const options: LaunchIntentOptions = {};

    if (executor.action) {
      options.action = executor.action;
    }
    if (executor.packageName) {
      options.packageName = executor.packageName;
    }
    if (executor.className) {
      options.className = executor.className;
    }

    const dataUri = normalizedUrl || executor.dataUri;
    if (dataUri) {
      options.dataUri = dataUri;
      if (!options.action) {
        options.action = "android.intent.action.VIEW";
      }
    }
    if (executor.mimeType) {
      options.mimeType = executor.mimeType;
    }
    if (executor.categories && executor.categories.length > 0) {
      options.categories = executor.categories;
    }
    if (executor.extras && Object.keys(executor.extras).length > 0) {
      options.extras = executor.extras;
    }

    const useOpenPackageOnly =
      Boolean(options.packageName) &&
      !options.action &&
      !options.className &&
      !options.dataUri &&
      !options.mimeType &&
      !options.categories &&
      !options.extras;

    const result = useOpenPackageOnly
      ? await MiuiPower.openPackage({ packageName: options.packageName ?? "" })
      : await MiuiPower.launchIntent(options);
    return formatOpenResult(label, result);
  });
}

async function runZone2Entry(entry: RuntimeZone2Entry) {
  if (entry.source === "builtin" && entry.builtinKey) {
    await runBuiltinAction(entry.builtinKey);
    return;
  }
  if (!entry.custom) {
    return;
  }
  await runCustomAction(entry.custom);
}

function locateInContainer(container: HTMLElement | null, locateKey: string) {
  if (!container) {
    return;
  }
  const target = container.querySelector<HTMLElement>(`[data-locate="${locateKey}"]`);
  if (!target) {
    return;
  }
  target.scrollIntoView({ behavior: "smooth", block: "center" });
  markHighlight(locateKey);
}

const searchSettingItems: SearchResultItem[] = [
  {
    id: "settings-save",
    pageId: "settings",
    locateKey: "settings-save",
    label: "Save / Revert",
    hint: "Settings",
    keywords: ["save", "revert", "settings", "draft"],
    groupId: "settings-save",
    groupScope: "settings",
  },
  {
    id: "settings-startup",
    pageId: "settings",
    locateKey: "settings-startup",
    label: "Startup Policy",
    hint: "Settings",
    keywords: ["startup", "policy", "remember", "fixed", "page", "theme", "light", "dark"],
    groupId: "settings-startup",
    groupScope: "settings",
  },
  {
    id: "settings-actions",
    pageId: "settings",
    locateKey: "settings-actions",
    label: "Button Placement",
    hint: "Settings",
    keywords: ["button", "placement", "layout", "size"],
    groupId: "settings-actions",
    groupScope: "settings",
  },
  {
    id: "settings-groups",
    pageId: "settings",
    locateKey: "settings-groups",
    label: "Group Management",
    hint: "Settings",
    keywords: ["group", "collapse", "title", "manage"],
    groupId: "settings-groups",
    groupScope: "settings",
  },
  {
    id: "settings-custom",
    pageId: "settings",
    locateKey: "settings-custom",
    label: "Custom Actions",
    hint: "Settings",
    keywords: ["custom", "intent", "action", "activity", "url", "experimental"],
    groupId: "settings-custom",
    groupScope: "settings",
  },
];

const searchResults = computed<SearchResultItem[]>(() => {
  const keyword = searchKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return [];
  }

  const groupTitleByKey = new Map(
    savedConfig.value.groups.map((group) => [runtimeGroupStateKey(group.pageId, group.id), group.title]),
  );

  const zone2Items = runtimeZone2Entries.value.map<SearchResultItem>((entry) => {
    const groupTitle = groupTitleByKey.get(runtimeGroupStateKey(entry.pageId, entry.groupId)) ?? "";
    return {
      id: `zone2-${entry.id}`,
      pageId: entry.pageId,
      locateKey: entry.locateKey,
      label: entry.label,
      hint: `${entry.pageId === "page1" ? "Page 1" : "Page 2"}${groupTitle ? ` · ${groupTitle}` : ""}`,
      keywords: [entry.label, groupTitle, ...entry.searchAliases].filter((item) => item.length > 0),
      groupId: entry.groupId,
      groupScope: "runtime",
    };
  });

  const source = [...zone2Items, ...searchSettingItems];
  return source
    .filter((item) =>
      item.keywords.some((term) => term.toLowerCase().includes(keyword)) ||
      item.label.toLowerCase().includes(keyword),
    )
    .slice(0, 6);
});

async function locateFromSearch(item: SearchResultItem) {
  searchKeyword.value = "";
  await switchPage(item.pageId);
  if (item.groupId && item.groupScope === "runtime" && (item.pageId === "page1" || item.pageId === "page2")) {
    setRuntimeGroupCollapsed(item.pageId, item.groupId, false);
  }
  if (item.groupId && item.groupScope === "settings" && isSettingsGroupId(item.groupId)) {
    setSettingsGroupCollapsed(item.groupId, false);
  }
  await nextTick();
  restorePageScroll(item.pageId);
  locateInContainer(pageScrollRef.value, item.locateKey);
}

function groupsForPage(pageId: ZonePageId) {
  return draftConfig.value.groups
    .filter((group) => group.pageId === pageId)
    .sort((left, right) => left.order - right.order || left.title.localeCompare(right.title));
}

function getFirstGroupId(pageId: ZonePageId) {
  return groupsForPage(pageId)[0]?.id ?? "";
}

function ensureGroupOnPage(pageId: ZonePageId) {
  if (groupsForPage(pageId).length > 0) {
    return;
  }
  draftConfig.value.groups.push({
    id: `auto-${pageId}-${Date.now().toString(36)}`,
    pageId,
    title: pageId === "page1" ? "页面一分组" : "页面二分组",
    order: 0,
    collapsedByDefault: false,
  });
  normalizeGroupOrders(pageId);
}

function allDraftRowsForGroup(pageId: ZonePageId, groupId: string) {
  const output: DraftPlacementRow[] = [];
  for (const item of draftConfig.value.builtinActions) {
    if (item.placement.pageId === pageId && item.placement.groupId === groupId) {
      output.push({
        id: `builtin:${item.actionKey}`,
        source: "builtin",
        label: actionByKey[item.actionKey]?.label ?? item.label,
        sourceLabel: "内置按钮",
        placement: item.placement,
        builtinKey: item.actionKey,
      });
    }
  }
  for (const item of draftConfig.value.customActions) {
    if (item.placement.pageId === pageId && item.placement.groupId === groupId) {
      output.push({
        id: `custom:${item.id}`,
        source: "custom",
        label: item.label,
        sourceLabel: "自定义按钮（实验）",
        placement: item.placement,
        customId: item.id,
      });
    }
  }
  return output.sort((left, right) => left.placement.order - right.placement.order || left.label.localeCompare(right.label));
}

function normalizeRowOrders(pageId: ZonePageId, groupId: string) {
  allDraftRowsForGroup(pageId, groupId).forEach((item, index) => {
    item.placement.order = index;
  });
}

function normalizeAllRowOrders() {
  (["page1", "page2"] as const).forEach((pageId) => {
    for (const group of groupsForPage(pageId)) {
      normalizeRowOrders(pageId, group.id);
    }
  });
}

function normalizeGroupOrders(pageId: ZonePageId) {
  groupsForPage(pageId).forEach((group, index) => {
    group.order = index;
  });
}

const draftPlacementRows = computed<DraftPlacementRow[]>(() => {
  const groupOrderById = new Map(
    draftConfig.value.groups.map((group) => [group.id, group.order]),
  );
  const output: DraftPlacementRow[] = [];

  for (const item of draftConfig.value.builtinActions) {
    const action = actionByKey[item.actionKey];
    if (!action) {
      continue;
    }
    output.push({
      id: `builtin:${item.actionKey}`,
      source: "builtin",
      label: action.label,
      sourceLabel: "内置按钮",
      placement: item.placement,
      builtinKey: item.actionKey,
    });
  }

  for (const item of draftConfig.value.customActions) {
    output.push({
      id: `custom:${item.id}`,
      source: "custom",
      label: item.label,
      sourceLabel: "自定义按钮（实验）",
      placement: item.placement,
      customId: item.id,
    });
  }

  return output.sort((left, right) => {
    if (left.placement.pageId !== right.placement.pageId) {
      return left.placement.pageId.localeCompare(right.placement.pageId);
    }
    const leftOrder = groupOrderById.get(left.placement.groupId) ?? 0;
    const rightOrder = groupOrderById.get(right.placement.groupId) ?? 0;
    if (leftOrder !== rightOrder) {
      return leftOrder - rightOrder;
    }
    if (left.placement.order !== right.placement.order) {
      return left.placement.order - right.placement.order;
    }
    return left.label.localeCompare(right.label);
  });
});

function readSelectValue(event: Event) {
  const element = event.target as HTMLSelectElement | null;
  return element?.value ?? "";
}

function updateRowPage(row: DraftPlacementRow, event: Event) {
  const pageId = readSelectValue(event);
  if (pageId !== "page1" && pageId !== "page2") {
    return;
  }
  if (row.placement.pageId === pageId) {
    return;
  }
  const previousPage = row.placement.pageId;
  const previousGroup = row.placement.groupId;
  row.placement.pageId = pageId;
  ensureGroupOnPage(pageId);
  row.placement.groupId = getFirstGroupId(pageId);
  row.placement.order = allDraftRowsForGroup(pageId, row.placement.groupId).length;
  normalizeRowOrders(previousPage, previousGroup);
  normalizeRowOrders(pageId, row.placement.groupId);
}

function updateRowGroup(row: DraftPlacementRow, event: Event) {
  const groupId = readSelectValue(event);
  if (!groupId || row.placement.groupId === groupId) {
    return;
  }
  const previousPage = row.placement.pageId;
  const previousGroup = row.placement.groupId;
  row.placement.groupId = groupId;
  row.placement.order = allDraftRowsForGroup(previousPage, groupId).length;
  normalizeRowOrders(previousPage, previousGroup);
  normalizeRowOrders(previousPage, groupId);
}

function updateRowSize(row: DraftPlacementRow, event: Event) {
  const size = readSelectValue(event);
  row.placement.size = size === "large" ? "large" : "small";
}

function moveRow(row: DraftPlacementRow, direction: -1 | 1) {
  const rows = allDraftRowsForGroup(row.placement.pageId, row.placement.groupId);
  const index = rows.findIndex((item) => item.id === row.id);
  const targetIndex = index + direction;
  if (index < 0 || targetIndex < 0 || targetIndex >= rows.length) {
    return;
  }
  const target = rows[targetIndex];
  const currentOrder = row.placement.order;
  row.placement.order = target.placement.order;
  target.placement.order = currentOrder;
  normalizeRowOrders(row.placement.pageId, row.placement.groupId);
}

const draftGroups = computed(() =>
  [...draftConfig.value.groups].sort((left, right) => {
    if (left.pageId !== right.pageId) {
      return left.pageId.localeCompare(right.pageId);
    }
    return left.order - right.order || left.title.localeCompare(right.title);
  }),
);

const newGroupForm = reactive({
  pageId: "page1" as ZonePageId,
  title: "",
  collapsedByDefault: false,
});

function groupHasRows(group: Zone2Group) {
  return allDraftRowsForGroup(group.pageId, group.id).length > 0;
}

function createGroup() {
  const title = newGroupForm.title.trim();
  if (!title) {
    message.value = "请先填写分组标题。";
    return;
  }
  const pageId = newGroupForm.pageId;
  draftConfig.value.groups.push({
    id: `group-${pageId}-${Date.now().toString(36)}`,
    pageId,
    title,
    order: groupsForPage(pageId).length,
    collapsedByDefault: newGroupForm.collapsedByDefault,
  });
  normalizeGroupOrders(pageId);
  newGroupForm.title = "";
  newGroupForm.collapsedByDefault = false;
  message.value = `已新增分组：${title}`;
}

function moveGroup(group: Zone2Group, direction: -1 | 1) {
  const groups = groupsForPage(group.pageId);
  const index = groups.findIndex((item) => item.id === group.id);
  const targetIndex = index + direction;
  if (index < 0 || targetIndex < 0 || targetIndex >= groups.length) {
    return;
  }
  const target = groups[targetIndex];
  const currentOrder = group.order;
  group.order = target.order;
  target.order = currentOrder;
  normalizeGroupOrders(group.pageId);
}

function deleteGroup(group: Zone2Group) {
  if (groupHasRows(group)) {
    message.value = "该分组仍有按钮，无法删除。";
    return;
  }
  const samePageGroups = groupsForPage(group.pageId);
  if (samePageGroups.length <= 1) {
    message.value = "每个页面至少保留一个分组。";
    return;
  }
  draftConfig.value.groups = draftConfig.value.groups.filter((item) => item.id !== group.id);
  normalizeGroupOrders(group.pageId);
}

const draftCustomActions = computed(() =>
  [...draftConfig.value.customActions].sort((left, right) => left.label.localeCompare(right.label)),
);

const editingCustomId = ref<string | null>(null);

const customForm = reactive<CustomFormState>({
  label: "",
  loadingLabel: "正在执行...",
  variant: "pink",
  searchAliasesText: "",
  pageId: "page2",
  groupId: "",
  size: "small",
  executorKind: "builtin_ref",
  builtinActionKey: editableBuiltinKeys[0],
  action: "",
  packageName: "",
  className: "",
  url: "",
  dataUri: "",
  mimeType: "",
  categoriesText: "",
  extrasJson: "",
});

function ensureCustomFormGroup() {
  ensureGroupOnPage(customForm.pageId);
  const options = groupsForPage(customForm.pageId);
  if (!options.some((group) => group.id === customForm.groupId)) {
    customForm.groupId = options[0]?.id ?? "";
  }
}

function startCreateCustom() {
  editingCustomId.value = null;
  customForm.label = "";
  customForm.loadingLabel = "正在执行...";
  customForm.variant = "pink";
  customForm.searchAliasesText = "";
  customForm.pageId = "page2";
  customForm.groupId = getFirstGroupId("page2");
  customForm.size = "small";
  customForm.executorKind = "builtin_ref";
  customForm.builtinActionKey = editableBuiltinKeys[0];
  customForm.action = "";
  customForm.packageName = "";
  customForm.className = "";
  customForm.url = "";
  customForm.dataUri = "";
  customForm.mimeType = "";
  customForm.categoriesText = "";
  customForm.extrasJson = "";
}

function editCustom(customId: string) {
  const item = draftConfig.value.customActions.find((action) => action.id === customId);
  if (!item) {
    return;
  }
  editingCustomId.value = item.id;
  customForm.label = item.label;
  customForm.loadingLabel = item.loadingLabel;
  customForm.variant = item.variant;
  customForm.searchAliasesText = item.searchAliases.join(", ");
  customForm.pageId = item.placement.pageId;
  customForm.groupId = item.placement.groupId;
  customForm.size = item.placement.size;

  if (item.executor.kind === "builtin_ref") {
    customForm.executorKind = "builtin_ref";
    customForm.builtinActionKey = item.executor.actionKey;
    customForm.action = "";
    customForm.packageName = "";
    customForm.className = "";
    customForm.url = "";
    customForm.dataUri = "";
    customForm.mimeType = "";
    customForm.categoriesText = "";
    customForm.extrasJson = "";
  } else {
    customForm.executorKind = "custom_intent";
    customForm.builtinActionKey = editableBuiltinKeys[0];
    customForm.action = item.executor.action ?? "";
    customForm.packageName = item.executor.packageName ?? "";
    customForm.className = item.executor.className ?? "";
    customForm.url = item.executor.url ?? "";
    customForm.dataUri = item.executor.dataUri ?? "";
    customForm.mimeType = item.executor.mimeType ?? "";
    customForm.categoriesText = (item.executor.categories ?? []).join(", ");
    customForm.extrasJson = item.executor.extras ? JSON.stringify(item.executor.extras) : "";
  }
  ensureCustomFormGroup();
}

function parseCustomAliases(text: string) {
  return text
    .split(",")
    .map((item) => item.trim())
    .filter((item, index, array) => item.length > 0 && array.indexOf(item) === index);
}

function parseCustomExtras(raw: string): Record<string, LaunchIntentExtraValue> | undefined | null {
  const text = raw.trim();
  if (!text) {
    return undefined;
  }

  try {
    const parsed = JSON.parse(text) as unknown;
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
      message.value = "Extras 必须是 JSON 对象。";
      return null;
    }
    const result: Record<string, LaunchIntentExtraValue> = {};
    for (const [key, value] of Object.entries(parsed)) {
      if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
        result[key] = value;
      }
    }
    return result;
  } catch {
    message.value = "Extras JSON 解析失败，请检查格式。";
    return null;
  }
}

function buildCustomActionFromForm(existing?: CustomAction) {
  const label = customForm.label.trim();
  if (!label) {
    message.value = "自定义按钮名称不能为空。";
    return null;
  }
  ensureCustomFormGroup();
  if (!customForm.groupId) {
    message.value = "请先选择分组。";
    return null;
  }

  let executor: CustomAction["executor"];
  if (customForm.executorKind === "builtin_ref") {
    if (!editableBuiltinKeySet.has(customForm.builtinActionKey)) {
      message.value = "请选择有效的内置动作。";
      return null;
    }
    executor = {
      kind: "builtin_ref",
      actionKey: customForm.builtinActionKey,
    };
  } else {
    const action = customForm.action.trim();
    const packageName = customForm.packageName.trim();
    const className = customForm.className.trim();
    const url = customForm.url.trim();
    const dataUri = customForm.dataUri.trim();
    const mimeType = customForm.mimeType.trim();
    const categories = parseCustomAliases(customForm.categoriesText);
    const extras = parseCustomExtras(customForm.extrasJson);
    if (extras === null) {
      return null;
    }

    const hasTarget = Boolean(action || packageName || className || url || dataUri);
    if (!hasTarget) {
      message.value = "自定义 Intent 至少填写一个目标（Action/包名/Activity/URL/Data URI）。";
      return null;
    }
    if (className && !packageName) {
      message.value = "填写 Activity 时必须同时填写包名。";
      return null;
    }

    const parsedExecutor: CustomIntentExecutor = {
      kind: "custom_intent",
    };
    if (action) {
      parsedExecutor.action = action;
    }
    if (packageName) {
      parsedExecutor.packageName = packageName;
    }
    if (className) {
      parsedExecutor.className = className;
    }
    if (url) {
      parsedExecutor.url = url;
    }
    if (dataUri) {
      parsedExecutor.dataUri = dataUri;
    }
    if (mimeType) {
      parsedExecutor.mimeType = mimeType;
    }
    if (categories.length > 0) {
      parsedExecutor.categories = categories;
    }
    if (extras && Object.keys(extras).length > 0) {
      parsedExecutor.extras = extras;
    }
    executor = parsedExecutor;
  }

  const nextPlacement: Zone2Placement = {
    pageId: customForm.pageId,
    groupId: customForm.groupId,
    size: customForm.size,
    order: existing
      ? existing.placement.order
      : allDraftRowsForGroup(customForm.pageId, customForm.groupId).length,
  };

  return {
    id: existing?.id ?? `custom-${Date.now().toString(36)}`,
    type: "custom",
    label,
    loadingLabel: customForm.loadingLabel.trim() || "正在执行...",
    variant: customForm.variant,
    experimental: true,
    searchAliases: parseCustomAliases(customForm.searchAliasesText),
    executor,
    placement: nextPlacement,
  } satisfies CustomAction;
}

function saveCustomForm() {
  const existing = editingCustomId.value
    ? draftConfig.value.customActions.find((item) => item.id === editingCustomId.value)
    : undefined;
  const next = buildCustomActionFromForm(existing);
  if (!next) {
    return;
  }

  if (!existing) {
    draftConfig.value.customActions.push(next);
    normalizeRowOrders(next.placement.pageId, next.placement.groupId);
    editingCustomId.value = next.id;
    message.value = `已新增自定义按钮：${next.label}`;
    return;
  }

  const previousPage = existing.placement.pageId;
  const previousGroup = existing.placement.groupId;
  Object.assign(existing, next);
  normalizeRowOrders(previousPage, previousGroup);
  normalizeRowOrders(existing.placement.pageId, existing.placement.groupId);
  message.value = `已更新自定义按钮：${next.label}`;
}

async function testCustomForm() {
  const existing = editingCustomId.value
    ? draftConfig.value.customActions.find((item) => item.id === editingCustomId.value)
    : undefined;
  const next = buildCustomActionFromForm(existing);
  if (!next) {
    return;
  }
  await runCustomAction(next, true);
}

async function testCustom(item: CustomAction) {
  await runCustomAction(item, true);
}

function removeCustom(customId: string) {
  const item = draftConfig.value.customActions.find((entry) => entry.id === customId);
  if (!item) {
    return;
  }
  draftConfig.value.customActions = draftConfig.value.customActions.filter(
    (entry) => entry.id !== customId,
  );
  normalizeRowOrders(item.placement.pageId, item.placement.groupId);
  if (editingCustomId.value === customId) {
    startCreateCustom();
  }
  message.value = `已删除自定义按钮：${item.label}`;
}

function saveDraftConfig() {
  normalizeAllRowOrders();
  const normalized = normalizeLauncherConfig(
    {
      version: LAUNCHER_CONFIG_VERSION,
      startupPolicy: draftConfig.value.startupPolicy,
      groups: draftConfig.value.groups,
      builtinActions: draftConfig.value.builtinActions,
      customActions: draftConfig.value.customActions,
    },
    defaultLauncherConfig,
    editableBuiltinKeySet,
  );
  savedConfig.value = normalized;
  draftConfig.value = cloneLauncherConfig(normalized);
  saveLauncherConfig(normalized);
  syncCollapsedGroupMap();
  message.value = "Configuration saved.";
}

function revertDraftConfig() {
  draftConfig.value = cloneLauncherConfig(savedConfig.value);
  startCreateCustom();
  message.value = "Reverted to last saved configuration.";
}

startCreateCustom();
ensureCustomFormGroup();
</script>

