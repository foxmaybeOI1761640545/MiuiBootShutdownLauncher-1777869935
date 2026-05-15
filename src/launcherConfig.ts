export const LAUNCHER_CONFIG_STORAGE_KEY = "launcher.config.v3";
const LEGACY_LAUNCHER_CONFIG_STORAGE_KEY = "launcher.config.v2";
export const LAUNCHER_CONFIG_VERSION = 3 as const;

export const LAUNCHER_UI_STATE_STORAGE_KEY = "launcher.ui-state.v1";
export const LAUNCHER_UI_STATE_VERSION = 1 as const;

export type ZonePageId = "page1" | "page2";
export type RuntimePageId = ZonePageId | "run" | "game" | "settings";
export type Zone2Size = "small" | "large";
export type ActionVariant = "pink" | "beige";
export type CustomExecutorKind = "builtin_ref" | "custom_intent";
export type CustomIntentExtraValue = string | number | boolean;
export type StartupPolicyMode = "remember" | "fixed";

export interface StartupPolicy {
  mode: StartupPolicyMode;
  fixedPageId: RuntimePageId;
}

export interface Zone2Placement {
  pageId: ZonePageId;
  groupId: string;
  order: number;
  size: Zone2Size;
}

export interface BuiltinAction {
  id: string;
  type: "builtin";
  actionKey: string;
  label: string;
  searchAliases: string[];
  placement: Zone2Placement;
}

export interface CustomBuiltinRefExecutor {
  kind: "builtin_ref";
  actionKey: string;
}

export interface CustomIntentExecutor {
  kind: "custom_intent";
  action?: string;
  packageName?: string;
  className?: string;
  dataUri?: string;
  url?: string;
  mimeType?: string;
  categories?: string[];
  extras?: Record<string, CustomIntentExtraValue>;
}

export type CustomExecutor = CustomBuiltinRefExecutor | CustomIntentExecutor;

export interface CustomAction {
  id: string;
  type: "custom";
  label: string;
  loadingLabel: string;
  variant: ActionVariant;
  experimental: true;
  searchAliases: string[];
  executor: CustomExecutor;
  placement: Zone2Placement;
}

export interface Zone2Group {
  id: string;
  pageId: ZonePageId;
  title: string;
  order: number;
  collapsedByDefault: boolean;
}

export interface LauncherConfigV2 {
  version: 2;
  groups: Zone2Group[];
  builtinActions: BuiltinAction[];
  customActions: CustomAction[];
}

export interface LauncherConfigV3 {
  version: 3;
  startupPolicy: StartupPolicy;
  groups: Zone2Group[];
  builtinActions: BuiltinAction[];
  customActions: CustomAction[];
}

export type LauncherConfig = LauncherConfigV3;

export interface LauncherUiState {
  version: 1;
  lastActivePage: RuntimePageId;
  collapsedGroups: Record<string, boolean>;
}

export interface BuiltinActionSeed {
  actionKey: string;
  label: string;
  searchAliases?: string[];
  placement: Zone2Placement;
}

export interface LauncherConfigDefaults {
  groups: Zone2Group[];
  builtinActions: BuiltinActionSeed[];
  startupPolicy?: StartupPolicy;
}

const PAGES: ZonePageId[] = ["page1", "page2"];
const DEFAULT_STARTUP_POLICY: StartupPolicy = {
  mode: "fixed",
  fixedPageId: "page1",
};

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function asString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function normalizeText(value: unknown): string {
  return (asString(value) ?? "").trim();
}

function normalizeSize(value: unknown): Zone2Size {
  return value === "large" ? "large" : "small";
}

function normalizeVariant(value: unknown): ActionVariant {
  return value === "beige" ? "beige" : "pink";
}

function normalizePageId(value: unknown, fallback: ZonePageId = "page2"): ZonePageId {
  return value === "page1" || value === "page2" ? value : fallback;
}

function normalizeRuntimePageId(value: unknown, fallback: RuntimePageId = "page1"): RuntimePageId {
  return value === "page1" || value === "page2" || value === "run" || value === "game" || value === "settings"
    ? value
    : fallback;
}

function normalizeStartupPolicyMode(
  value: unknown,
  fallback: StartupPolicyMode = "fixed",
): StartupPolicyMode {
  return value === "remember" || value === "fixed" ? value : fallback;
}

function normalizeStartupPolicy(raw: unknown, fallback: StartupPolicy): StartupPolicy {
  const source = isObject(raw) ? raw : {};
  return {
    mode: normalizeStartupPolicyMode(source.mode, fallback.mode),
    fixedPageId: normalizeRuntimePageId(source.fixedPageId, "page1"),
  };
}

function normalizeAliases(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }

  const seen = new Set<string>();
  const output: string[] = [];
  for (const item of value) {
    const text = normalizeText(item);
    if (!text || seen.has(text)) {
      continue;
    }
    seen.add(text);
    output.push(text);
  }
  return output;
}

function normalizeId(raw: unknown, fallback: string): string {
  const value = normalizeText(raw).replace(/[^a-zA-Z0-9_-]/g, "");
  return value || fallback;
}

function deepClone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function buildGroupsByPage(groups: Zone2Group[]) {
  const map = new Map<ZonePageId, Zone2Group[]>();
  for (const pageId of PAGES) {
    map.set(
      pageId,
      groups
        .filter((group) => group.pageId === pageId)
        .sort((left, right) => left.order - right.order || left.title.localeCompare(right.title)),
    );
  }
  return map;
}

function ensurePageGroups(groups: Zone2Group[], defaults: Zone2Group[]) {
  const used = new Set(groups.map((group) => group.id));
  for (const pageId of PAGES) {
    if (groups.some((group) => group.pageId === pageId)) {
      continue;
    }

    const defaultForPage = defaults.find((group) => group.pageId === pageId);
    if (defaultForPage) {
      let id = defaultForPage.id;
      let suffix = 1;
      while (used.has(id)) {
        id = `${defaultForPage.id}-${suffix}`;
        suffix += 1;
      }
      used.add(id);
      groups.push({
        id,
        pageId,
        title: defaultForPage.title,
        order: 0,
        collapsedByDefault: defaultForPage.collapsedByDefault,
      });
      continue;
    }

    let fallbackId = `group-${pageId}-default`;
    let suffix = 1;
    while (used.has(fallbackId)) {
      fallbackId = `group-${pageId}-default-${suffix}`;
      suffix += 1;
    }
    used.add(fallbackId);
    groups.push({
      id: fallbackId,
      pageId,
      title: pageId === "page1" ? "页面一分组" : "页面二分组",
      order: 0,
      collapsedByDefault: false,
    });
  }
}

function normalizeGroups(raw: unknown, defaults: Zone2Group[]): Zone2Group[] {
  const parsed: Zone2Group[] = [];
  const used = new Set<string>();
  const rawGroups = Array.isArray(raw) ? raw : [];

  for (let index = 0; index < rawGroups.length; index += 1) {
    const item = rawGroups[index];
    if (!isObject(item)) {
      continue;
    }

    const pageId = normalizePageId(item.pageId, "page2");
    const id = normalizeId(item.id, `group-${pageId}-${index + 1}`);
    if (used.has(id)) {
      continue;
    }

    used.add(id);
    parsed.push({
      id,
      pageId,
      title: normalizeText(item.title) || "未命名分组",
      order: Number.isFinite(item.order) ? Number(item.order) : index,
      collapsedByDefault: Boolean(item.collapsedByDefault),
    });
  }

  const groups = parsed.length > 0 ? parsed : deepClone(defaults);
  ensurePageGroups(groups, defaults);

  const normalized: Zone2Group[] = [];
  for (const pageId of PAGES) {
    const pageGroups = groups
      .filter((group) => group.pageId === pageId)
      .sort((left, right) => left.order - right.order || left.title.localeCompare(right.title));
    pageGroups.forEach((group, index) => {
      normalized.push({
        ...group,
        pageId,
        order: index,
      });
    });
  }
  return normalized;
}

function sanitizePlacement(
  raw: unknown,
  fallback: Zone2Placement,
  groupsByPage: Map<ZonePageId, Zone2Group[]>,
): Zone2Placement {
  const item = isObject(raw) ? raw : {};
  const fallbackPageId = fallback.pageId;
  let pageId = normalizePageId(item.pageId, fallbackPageId);
  let groups = groupsByPage.get(pageId) ?? [];

  if (groups.length === 0) {
    pageId = fallbackPageId;
    groups = groupsByPage.get(pageId) ?? [];
  }

  const fallbackGroupId = groups[0]?.id ?? fallback.groupId;
  const candidateGroupId = normalizeText(item.groupId) || fallback.groupId;
  const groupId = groups.some((group) => group.id === candidateGroupId) ? candidateGroupId : fallbackGroupId;
  const order = Number.isFinite(item.order) ? Math.max(0, Math.floor(Number(item.order))) : fallback.order;

  return {
    pageId,
    groupId,
    order,
    size: normalizeSize(item.size ?? fallback.size),
  };
}

function normalizeBuiltinActions(
  raw: unknown,
  defaults: BuiltinAction[],
  validBuiltinKeys: Set<string>,
  groupsByPage: Map<ZonePageId, Zone2Group[]>,
): BuiltinAction[] {
  const rawItems = Array.isArray(raw) ? raw : [];
  const rawByKey = new Map<string, Record<string, unknown>>();

  for (const item of rawItems) {
    if (!isObject(item)) {
      continue;
    }
    const actionKey = normalizeText(item.actionKey);
    if (!actionKey || !validBuiltinKeys.has(actionKey) || rawByKey.has(actionKey)) {
      continue;
    }
    rawByKey.set(actionKey, item);
  }

  const merged: BuiltinAction[] = [];
  const used = new Set<string>();

  for (const base of defaults) {
    const overlay = rawByKey.get(base.actionKey);
    const searchAliases = overlay ? normalizeAliases(overlay.searchAliases) : base.searchAliases;
    const placement = sanitizePlacement(overlay?.placement, base.placement, groupsByPage);

    merged.push({
      ...base,
      searchAliases: searchAliases.length > 0 ? searchAliases : base.searchAliases,
      placement,
    });
    used.add(base.actionKey);
  }

  for (const [actionKey, item] of rawByKey.entries()) {
    if (used.has(actionKey)) {
      continue;
    }

    const fallbackPageId: ZonePageId = "page2";
    const fallbackGroupId = groupsByPage.get(fallbackPageId)?.[0]?.id ?? "";
    merged.push({
      id: `builtin-${actionKey}`,
      type: "builtin",
      actionKey,
      label: normalizeText(item.label) || actionKey,
      searchAliases: normalizeAliases(item.searchAliases),
      placement: sanitizePlacement(
        item.placement,
        {
          pageId: fallbackPageId,
          groupId: fallbackGroupId,
          order: 9999,
          size: "small",
        },
        groupsByPage,
      ),
    });
  }

  return merged;
}

function sanitizeCustomExecutor(
  raw: unknown,
  fallbackBuiltinKey: string,
  validBuiltinKeys: Set<string>,
): CustomExecutor {
  if (!isObject(raw)) {
    return {
      kind: "builtin_ref",
      actionKey: fallbackBuiltinKey,
    };
  }

  const kind = normalizeText(raw.kind) as CustomExecutorKind;
  if (kind === "builtin_ref") {
    const actionKey = normalizeText(raw.actionKey);
    return {
      kind: "builtin_ref",
      actionKey: validBuiltinKeys.has(actionKey) ? actionKey : fallbackBuiltinKey,
    };
  }

  const extras: Record<string, CustomIntentExtraValue> = {};
  if (isObject(raw.extras)) {
    for (const [key, value] of Object.entries(raw.extras)) {
      if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
        extras[key] = value;
      }
    }
  }

  const categories = normalizeAliases(raw.categories);
  const executor: CustomIntentExecutor = {
    kind: "custom_intent",
  };

  const action = normalizeText(raw.action);
  if (action) {
    executor.action = action;
  }
  const packageName = normalizeText(raw.packageName);
  if (packageName) {
    executor.packageName = packageName;
  }
  const className = normalizeText(raw.className);
  if (className && packageName) {
    executor.className = className;
  }
  const dataUri = normalizeText(raw.dataUri);
  if (dataUri) {
    executor.dataUri = dataUri;
  }
  const url = normalizeText(raw.url);
  if (url) {
    executor.url = url;
  }
  const mimeType = normalizeText(raw.mimeType);
  if (mimeType) {
    executor.mimeType = mimeType;
  }
  if (categories.length > 0) {
    executor.categories = categories;
  }
  if (Object.keys(extras).length > 0) {
    executor.extras = extras;
  }

  return executor;
}

function normalizeCustomActions(
  raw: unknown,
  groupsByPage: Map<ZonePageId, Zone2Group[]>,
  validBuiltinKeys: Set<string>,
): CustomAction[] {
  const fallbackBuiltinKey = [...validBuiltinKeys][0] ?? "";
  const rawItems = Array.isArray(raw) ? raw : [];
  const normalized: CustomAction[] = [];
  const usedIds = new Set<string>();

  for (let index = 0; index < rawItems.length; index += 1) {
    const item = rawItems[index];
    if (!isObject(item)) {
      continue;
    }

    const id = normalizeId(item.id, `custom-${index + 1}`);
    if (usedIds.has(id)) {
      continue;
    }
    usedIds.add(id);

    const placement = sanitizePlacement(
      item.placement,
      {
        pageId: "page2",
        groupId: groupsByPage.get("page2")?.[0]?.id ?? groupsByPage.get("page1")?.[0]?.id ?? "",
        order: 9999,
        size: "small",
      },
      groupsByPage,
    );

    const executor = sanitizeCustomExecutor(item.executor, fallbackBuiltinKey, validBuiltinKeys);
    normalized.push({
      id,
      type: "custom",
      label: normalizeText(item.label) || `自定义按钮${index + 1}`,
      loadingLabel: normalizeText(item.loadingLabel) || "正在执行...",
      variant: normalizeVariant(item.variant),
      experimental: true,
      searchAliases: normalizeAliases(item.searchAliases),
      placement,
      executor,
    });
  }

  return normalized;
}

function resequenceOrders(config: LauncherConfigV3) {
  const buckets = new Map<string, Array<{ id: string; label: string; placement: Zone2Placement }>>();

  const put = (id: string, label: string, placement: Zone2Placement) => {
    const key = `${placement.pageId}|${placement.groupId}`;
    const list = buckets.get(key) ?? [];
    list.push({ id, label, placement });
    buckets.set(key, list);
  };

  for (const item of config.builtinActions) {
    put(item.id, item.label, item.placement);
  }
  for (const item of config.customActions) {
    put(item.id, item.label, item.placement);
  }

  for (const entries of buckets.values()) {
    entries
      .sort((left, right) => left.placement.order - right.placement.order || left.label.localeCompare(right.label))
      .forEach((entry, index) => {
        entry.placement.order = index;
      });
  }
}

export function buildDefaultLauncherConfig(defaults: LauncherConfigDefaults): LauncherConfigV3 {
  const groups = defaults.groups.map((group) => ({
    ...group,
    id: normalizeId(group.id, `group-${group.pageId}`),
    title: normalizeText(group.title) || "未命名分组",
  }));

  const builtinActions: BuiltinAction[] = defaults.builtinActions.map((item) => ({
    id: `builtin-${item.actionKey}`,
    type: "builtin",
    actionKey: item.actionKey,
    label: item.label,
    searchAliases: item.searchAliases ?? [],
    placement: {
      pageId: item.placement.pageId,
      groupId: item.placement.groupId,
      order: item.placement.order,
      size: item.placement.size,
    },
  }));

  const config: LauncherConfigV3 = {
    version: LAUNCHER_CONFIG_VERSION,
    startupPolicy: normalizeStartupPolicy(defaults.startupPolicy, DEFAULT_STARTUP_POLICY),
    groups,
    builtinActions,
    customActions: [],
  };

  const validBuiltinKeys = new Set(builtinActions.map((item) => item.actionKey));
  return normalizeLauncherConfig(config, config, validBuiltinKeys);
}

export function cloneLauncherConfig(config: LauncherConfigV3): LauncherConfigV3 {
  return deepClone(config);
}

export function normalizeLauncherConfig(
  raw: unknown,
  defaults: LauncherConfigV3,
  validBuiltinKeys: Set<string>,
): LauncherConfigV3 {
  const source = isObject(raw) ? raw : {};
  const groups = normalizeGroups(source.groups, defaults.groups);
  const groupsByPage = buildGroupsByPage(groups);

  const builtinActions = normalizeBuiltinActions(
    source.builtinActions,
    defaults.builtinActions,
    validBuiltinKeys,
    groupsByPage,
  );
  const customActions = normalizeCustomActions(source.customActions, groupsByPage, validBuiltinKeys);
  const startupPolicy = normalizeStartupPolicy(source.startupPolicy, defaults.startupPolicy);

  const normalized: LauncherConfigV3 = {
    version: LAUNCHER_CONFIG_VERSION,
    startupPolicy,
    groups,
    builtinActions,
    customActions,
  };

  resequenceOrders(normalized);
  return normalized;
}

export function loadLauncherConfig(
  defaults: LauncherConfigV3,
  validBuiltinKeys: Set<string>,
): LauncherConfigV3 {
  if (typeof window === "undefined") {
    return cloneLauncherConfig(defaults);
  }

  const v3Text = window.localStorage.getItem(LAUNCHER_CONFIG_STORAGE_KEY);
  const legacyText = window.localStorage.getItem(LEGACY_LAUNCHER_CONFIG_STORAGE_KEY);
  const rawText = v3Text ?? legacyText;

  if (!rawText) {
    return cloneLauncherConfig(defaults);
  }

  try {
    const rawParsed = JSON.parse(rawText) as unknown;
    const normalized = normalizeLauncherConfig(rawParsed, defaults, validBuiltinKeys);
    if (!v3Text) {
      window.localStorage.setItem(LAUNCHER_CONFIG_STORAGE_KEY, JSON.stringify(normalized));
    }
    return normalized;
  } catch {
    return cloneLauncherConfig(defaults);
  }
}

export function saveLauncherConfig(config: LauncherConfigV3) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(LAUNCHER_CONFIG_STORAGE_KEY, JSON.stringify(config));
}

function normalizeBooleanMap(raw: unknown): Record<string, boolean> {
  if (!isObject(raw)) {
    return {};
  }
  const output: Record<string, boolean> = {};
  for (const [key, value] of Object.entries(raw)) {
    const normalizedKey = normalizeText(key);
    if (!normalizedKey) {
      continue;
    }
    output[normalizedKey] = Boolean(value);
  }
  return output;
}

function normalizeLauncherUiState(raw: unknown, defaults: LauncherUiState): LauncherUiState {
  const source = isObject(raw) ? raw : {};
  return {
    version: LAUNCHER_UI_STATE_VERSION,
    lastActivePage: normalizeRuntimePageId(source.lastActivePage, defaults.lastActivePage),
    collapsedGroups: normalizeBooleanMap(source.collapsedGroups),
  };
}

export function buildDefaultLauncherUiState(): LauncherUiState {
  return {
    version: LAUNCHER_UI_STATE_VERSION,
    lastActivePage: "page1",
    collapsedGroups: {},
  };
}

export function loadLauncherUiState(defaults: LauncherUiState = buildDefaultLauncherUiState()): LauncherUiState {
  if (typeof window === "undefined") {
    return deepClone(defaults);
  }

  const rawText = window.localStorage.getItem(LAUNCHER_UI_STATE_STORAGE_KEY);
  if (!rawText) {
    return deepClone(defaults);
  }

  try {
    const rawParsed = JSON.parse(rawText) as unknown;
    return normalizeLauncherUiState(rawParsed, defaults);
  } catch {
    return deepClone(defaults);
  }
}

export function saveLauncherUiState(state: LauncherUiState) {
  if (typeof window === "undefined") {
    return;
  }
  const normalized = normalizeLauncherUiState(state, buildDefaultLauncherUiState());
  window.localStorage.setItem(LAUNCHER_UI_STATE_STORAGE_KEY, JSON.stringify(normalized));
}
