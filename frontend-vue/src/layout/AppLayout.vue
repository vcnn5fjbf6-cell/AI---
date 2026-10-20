<template>
  <div class="layout-shell">
    <aside
      id="app-primary-navigation"
      class="layout-sidebar"
      :class="{ 'is-mobile-open': mobileSidebarOpen }"
      aria-label="主导航菜单"
      :aria-hidden="isNarrowViewport ? !mobileSidebarOpen : undefined"
      :aria-modal="isNarrowViewport && mobileSidebarOpen ? 'true' : undefined"
      :inert="isNarrowViewport && !mobileSidebarOpen"
      :role="isNarrowViewport ? 'dialog' : undefined"
    >
      <div class="mobile-sidebar-head">
        <div class="brand-card">
          <div class="brand-icon">运</div>
          <div>
            <div class="brand-title">酒仙桥区域运维平台</div>
            <div class="brand-sub">Regional Operations Console</div>
          </div>
        </div>
        <button
          ref="mobileSidebarCloseButton"
          class="mobile-sidebar-close"
          type="button"
          aria-label="关闭主导航菜单"
          @click="closeMobileSidebar(true)"
        >
          <el-icon aria-hidden="true"><Close /></el-icon>
        </button>
      </div>
      <el-button class="feedback-entry" :icon="ChatDotSquare" @click="feedbackDialogVisible = true">
        意见反馈
      </el-button>

      <el-scrollbar class="menu-scroll">
        <el-menu
          :default-active="activeMenuPath"
          :default-openeds="openMenuPaths"
          class="vnet-menu"
          router
          unique-opened
          @select="closeMobileSidebar(true)"
        >
          <template v-for="menu in visibleMenus" :key="menu.path">
            <el-sub-menu v-if="menu.children && menu.children.length > 0" :index="menu.path">
              <template #title>{{ menu.name }}</template>
              <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
                {{ child.name }}
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="menu.path">
              {{ menu.name }}
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>

      <div class="app-version">系统版本 {{ appVersion }}</div>
    </aside>

    <button
      class="mobile-sidebar-backdrop"
      :class="{ 'is-visible': isNarrowViewport && mobileSidebarOpen }"
      type="button"
      aria-label="关闭主导航菜单"
      :aria-hidden="!isNarrowViewport || !mobileSidebarOpen"
      :tabindex="isNarrowViewport && mobileSidebarOpen ? 0 : -1"
      @click="closeMobileSidebar(true)"
    ></button>

    <div class="layout-main-shell" :inert="isNarrowViewport && mobileSidebarOpen">
      <header class="layout-header">
        <div class="header-leading">
          <button
            ref="mobileMenuTrigger"
            class="mobile-menu-trigger"
            type="button"
            aria-label="打开主导航菜单"
            aria-controls="app-primary-navigation"
            :aria-expanded="mobileSidebarOpen"
            @click="openMobileSidebar"
          >
            <el-icon aria-hidden="true"><Menu /></el-icon>
          </button>
          <div class="header-copy">
            <p>{{ todayLabel }}</p>
            <h1>{{ pageTitle }}</h1>
          </div>
        </div>

        <div class="header-actions">
          <el-popover
            v-if="showRequestMessages"
            v-model:visible="messagePopoverVisible"
            placement="bottom-end"
            width="380"
            trigger="click"
            @show="loadRejectedMessages"
          >
            <template #reference>
              <el-badge :value="unreadMessageCount" :hidden="unreadMessageCount === 0" class="message-badge">
                <el-button :icon="Bell" circle aria-label="消息" />
              </el-badge>
            </template>

            <div class="message-panel">
              <div class="message-panel-head">
                <strong>消息<span v-if="unreadMessageCount > 0">未读 {{ unreadMessageCount }}</span></strong>
                <div class="message-panel-actions">
                  <el-button link type="primary" :loading="messageLoading" @click="loadRejectedMessages">刷新</el-button>
                  <el-button
                    v-if="unreadMessageCount > 0"
                    link
                    type="primary"
                    :loading="messageReadLoading"
                    @click="markAllMessagesRead"
                  >
                    全部已读
                  </el-button>
                  <el-button
                    v-if="visibleMessages.length > 0"
                    link
                    type="danger"
                    :loading="messageClearLoading"
                    @click="clearAllMessages"
                  >
                    一键清空
                  </el-button>
                </div>
              </div>
              <div v-if="messageLoading" class="message-empty">消息加载中...</div>
              <div v-else-if="visibleMessages.length === 0" class="message-empty">暂无采购提醒</div>
              <div v-else class="message-list">
                <div
                  v-for="message in visibleMessages"
                  :key="messageKey(message)"
                  class="message-item"
                  :class="{ 'is-read': isMessageRead(message) }"
                  role="button"
                  tabindex="0"
                  @click="markMessageRead(message)"
                  @keyup.enter="markMessageRead(message)"
                >
                  <div class="message-item-main">
                    <div class="message-title-row">
                      <strong>{{ messageTitle(message) }}</strong>
                      <el-tag size="small" :type="isMessageRead(message) ? 'info' : 'warning'">
                        {{ isMessageRead(message) ? "已读" : "未读" }}
                      </el-tag>
                    </div>
                    <span>{{ message.reqTitle || "采购申请" }}</span>
                    <p>{{ messageSummary(message) }}</p>
                    <small>{{ formatMessageTime(message.updatedAt) }}</small>
                  </div>
                  <div class="message-item-actions">
                    <el-button link type="primary" @click.stop="goResubmitRequest(message)">重新提交</el-button>
                    <el-button link type="danger" @click.stop="clearMessage(message)">清除</el-button>
                  </div>
                </div>
              </div>
            </div>
          </el-popover>

          <div class="account-card">
            <div class="account-meta">
              <span>当前账号</span>
              <strong>{{ authStore.realName || authStore.username }}</strong>
            </div>
            <el-dropdown trigger="click" @command="handleCommand">
              <el-button type="primary" plain>账号</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="password">修改密码</el-dropdown-item>
                  <el-dropdown-item v-if="!isSupplierAccount" command="access">我的权限</el-dropdown-item>
                  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </header>

      <main class="layout-main">
        <router-view />
      </main>
    </div>

    <PasswordChangeDialog v-model="passwordDialogVisible" :required="passwordChangeRequired" />
    <FeedbackDialog v-model="feedbackDialogVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Bell, ChatDotSquare, Close, Menu } from "@element-plus/icons-vue";
import PasswordChangeDialog from "../components/account/PasswordChangeDialog.vue";
import FeedbackDialog from "../components/feedback/FeedbackDialog.vue";
import {
  clearRejectedMessagesApi,
  getRejectedItemMessagesApi,
  getRejectedMessageClearKeysApi,
  getRejectedMessageReadKeysApi,
  getWorkflowPageApi,
  markRejectedMessagesReadApi
} from "../api/workflow";
import { filterMenusByRoles, menuTree } from "../config/menu";
import { CURRENT_SYSTEM_VERSION } from "../config/systemUpdates";
import { useAuthStore } from "../stores/auth";
import type { PurchaseRequestVO } from "../types/purchase";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const appVersion = CURRENT_SYSTEM_VERSION;
const passwordDialogVisible = ref(false);
const feedbackDialogVisible = ref(false);
const messagePopoverVisible = ref(false);
const messageLoading = ref(false);
const messageReadLoading = ref(false);
const messageClearLoading = ref(false);
const rejectedMessages = ref<PurchaseRequestVO[]>([]);
const messageReadKeys = ref<Set<string>>(new Set());
const messageClearedKeys = ref<Set<string>>(new Set());
const mobileSidebarOpen = ref(false);
const mobileMenuTrigger = ref<HTMLButtonElement | null>(null);
const mobileSidebarCloseButton = ref<HTMLButtonElement | null>(null);
const mobileSidebarMediaQuery = "(max-width: 980px)";
const isNarrowViewport = ref(typeof window !== "undefined" && window.matchMedia(mobileSidebarMediaQuery).matches);
let mobileSidebarMedia: MediaQueryList | null = null;

const visibleMenus = computed(() => filterMenusByRoles(menuTree, authStore.effectiveRoleCodes, authStore.appCodes, authStore.permissionCodes));
const isSupplierAccount = computed(() => {
  const userType = authStore.user?.userType?.trim().toUpperCase();
  return userType === "SUPPLIER"
    || userType === "SUPPLIER_QUOTE_VIEWER"
    || authStore.effectiveRoleCodes.some((role) => ["SUPPLIER", "SUPPLIER_QUOTE_VIEWER"].includes(role.toUpperCase()))
    || authStore.user?.supplierId != null
    || authStore.hasAnyPermission(["supplier_quote:view-all", "procurement_order:supplier-manage-all"]);
});
const passwordChangeRequired = computed(() => Boolean(authStore.user?.passwordExpired || authStore.user?.mustChangePassword === 1));
const showRequestMessages = computed(() => !isSupplierAccount.value && authStore.hasApp("PROCUREMENT") && Boolean(authStore.user?.userId));
const visibleMessages = computed(() => rejectedMessages.value.filter((message) => !isMessageCleared(message)));
const unreadMessages = computed(() => visibleMessages.value.filter((message) => !isMessageRead(message)));
const unreadMessageCount = computed(() => unreadMessages.value.length);

const pageTitle = computed(() => {
  const title = route.meta.title as string | undefined;
  return title || "酒仙桥区域运维平台";
});

const activeMenuPath = computed(() => {
  if (route.path.startsWith("/maintenance/review/")) return "/maintenance/review/demo-parse";
  return route.path;
});

const openMenuPaths = computed(() => {
  if (route.path.startsWith("/workbench")) return [];
  if (route.path.startsWith("/ehs")) return ["/ehs"];
  if (route.path.startsWith("/engineering-outsourcing")) return ["/engineering-outsourcing"];
  if (route.path.startsWith("/disaster-weather")) return ["/disaster-weather"];
  if (route.path.startsWith("/platform")) return ["/platform"];
  if (route.path.startsWith("/procurement")) return ["/procurement"];
  return ["/maintenance"];
});

const todayLabel = computed(() =>
  new Intl.DateTimeFormat("zh-CN", {
    month: "long",
    day: "numeric",
    weekday: "long"
  }).format(new Date())
);

async function handleCommand(command: string): Promise<void> {
  if (command === "password") {
    passwordDialogVisible.value = true;
    return;
  }
  if (command === "access") {
    await router.push("/platform/access");
    return;
  }
  if (command === "logout") {
    await authStore.logout();
    await router.push("/login");
  }
}

async function loadRejectedMessages(): Promise<void> {
  if (!showRequestMessages.value || !authStore.user?.userId) {
    rejectedMessages.value = [];
    messageReadKeys.value = new Set();
    messageClearedKeys.value = new Set();
    return;
  }
  messageLoading.value = true;
  try {
    const [requestData, itemMessages, readKeys, clearedKeys] = await Promise.all([
      getWorkflowPageApi({
        pageNo: 1,
        pageSize: 20,
        status: "REJECTED",
        approvalStatus: "REJECTED",
        applicantUserId: authStore.user.userId
      }),
      getRejectedItemMessagesApi(),
      getRejectedMessageReadKeysApi(),
      getRejectedMessageClearKeysApi()
    ]);
    messageReadKeys.value = new Set(readKeys);
    messageClearedKeys.value = new Set(clearedKeys);
    rejectedMessages.value = mergeMessages([...itemMessages, ...requestData.records]).slice(0, 50);
  } finally {
    messageLoading.value = false;
  }
}

async function markMessageRead(message: PurchaseRequestVO): Promise<void> {
  const key = messageKey(message);
  if (messageReadKeys.value.has(key)) {
    return;
  }
  await markRejectedMessagesReadApi([key]);
  messageReadKeys.value = new Set([...messageReadKeys.value, key]);
}

async function markAllMessagesRead(): Promise<void> {
  const keys = unreadMessages.value.map(messageKey);
  if (keys.length === 0) {
    return;
  }
  messageReadLoading.value = true;
  try {
    await markRejectedMessagesReadApi(keys);
    messageReadKeys.value = new Set([...messageReadKeys.value, ...keys]);
  } finally {
    messageReadLoading.value = false;
  }
}

async function clearMessage(message: PurchaseRequestVO): Promise<void> {
  const key = messageKey(message);
  await clearRejectedMessagesApi([key]);
  messageReadKeys.value = new Set([...messageReadKeys.value, key]);
  messageClearedKeys.value = new Set([...messageClearedKeys.value, key]);
}

async function clearAllMessages(): Promise<void> {
  const keys = visibleMessages.value.map(messageKey);
  if (keys.length === 0) {
    return;
  }
  messageClearLoading.value = true;
  try {
    await clearRejectedMessagesApi(keys);
    messageReadKeys.value = new Set([...messageReadKeys.value, ...keys]);
    messageClearedKeys.value = new Set([...messageClearedKeys.value, ...keys]);
  } finally {
    messageClearLoading.value = false;
  }
}

async function goResubmitRequest(message: PurchaseRequestVO): Promise<void> {
  await markMessageRead(message);
  messagePopoverVisible.value = false;
  const rejectedOnly = (message.items ?? []).some((item) => item.status === "REJECTED");
  await router.push({
    path: `/procurement/requests/${message.id}/edit`,
    query: rejectedOnly ? { rejectedOnly: "1" } : {}
  });
}

function mergeMessages(messages: PurchaseRequestVO[]): PurchaseRequestVO[] {
  const map = new Map<number, PurchaseRequestVO>();
  for (const message of messages) {
    if (!map.has(message.id)) {
      map.set(message.id, message);
    }
  }
  return [...map.values()].sort((left, right) => String(right.updatedAt || "").localeCompare(String(left.updatedAt || "")));
}

function messageKey(message: PurchaseRequestVO): string {
  const rejectedItemKeys = (message.items ?? [])
    .filter((item) => item.status === "REJECTED")
    .map((item) => item.id ?? item.lineNo ?? item.itemCode ?? item.itemName ?? "")
    .filter((value) => value !== "")
    .sort()
    .join(",");
  const version = message.updatedAt || message.approvedAt || message.submittedAt || message.createdAt || "";
  return `purchase-rejected:${message.id}:${version}:${rejectedItemKeys}`;
}

function isMessageRead(message: PurchaseRequestVO): boolean {
  return messageReadKeys.value.has(messageKey(message));
}

function isMessageCleared(message: PurchaseRequestVO): boolean {
  return messageClearedKeys.value.has(messageKey(message));
}

function messageTitle(message: PurchaseRequestVO): string {
  const count = message.items?.filter((item) => item.status === "REJECTED").length ?? 0;
  if (count > 0) {
    return `${message.reqNo} 有 ${count} 个商品被驳回`;
  }
  return `${message.reqNo} 已被驳回`;
}

function messageSummary(message: PurchaseRequestVO): string {
  const rejectedItems = message.items?.filter((item) => item.status === "REJECTED") ?? [];
  if (rejectedItems.length > 0) {
    const names = rejectedItems.slice(0, 3).map((item) => item.itemName || item.itemCode || "商品").join("、");
    return `${names} 已被驳回。如有再次采购需求，可以重新提交。`;
  }
  return "如有再次采购需求，可以修改后重新提交。";
}

function formatMessageTime(value?: string | null): string {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function hasPasswordQuery(value: unknown): boolean {
  if (Array.isArray(value)) {
    return value.some((item) => item != null && item !== "0");
  }
  return value != null && value !== "0";
}

function openMobileSidebar(): void {
  if (!isNarrowViewport.value) {
    return;
  }
  mobileSidebarOpen.value = true;
  void nextTick(() => mobileSidebarCloseButton.value?.focus());
}

function closeMobileSidebar(restoreFocus = false): void {
  if (!mobileSidebarOpen.value) {
    return;
  }
  mobileSidebarOpen.value = false;
  if (restoreFocus) {
    void nextTick(() => mobileMenuTrigger.value?.focus());
  }
}

function handleMobileSidebarMediaChange(event: MediaQueryListEvent): void {
  isNarrowViewport.value = event.matches;
  if (!event.matches) {
    closeMobileSidebar();
  }
}

function handleLayoutKeydown(event: KeyboardEvent): void {
  if (event.key === "Escape" && mobileSidebarOpen.value) {
    closeMobileSidebar(true);
  }
}

onMounted(() => {
  mobileSidebarMedia = window.matchMedia(mobileSidebarMediaQuery);
  isNarrowViewport.value = mobileSidebarMedia.matches;
  mobileSidebarMedia.addEventListener("change", handleMobileSidebarMediaChange);
  window.addEventListener("keydown", handleLayoutKeydown);

  if (passwordChangeRequired.value || hasPasswordQuery(route.query.password)) {
    passwordDialogVisible.value = true;
  }
  void loadRejectedMessages();
});

onBeforeUnmount(() => {
  mobileSidebarMedia?.removeEventListener("change", handleMobileSidebarMediaChange);
  window.removeEventListener("keydown", handleLayoutKeydown);
  document.body.classList.remove("mobile-nav-open");
});

watch(
  () => [route.query.password, passwordChangeRequired.value],
  ([passwordQuery, required]) => {
    if (required || hasPasswordQuery(passwordQuery)) {
      passwordDialogVisible.value = true;
    }
  }
);

watch(
  () => [authStore.user?.userId, route.fullPath],
  () => {
    void loadRejectedMessages();
  }
);

watch(
  () => route.fullPath,
  () => {
    closeMobileSidebar();
  }
);

watch([isNarrowViewport, mobileSidebarOpen], ([narrowViewport, sidebarOpen]) => {
  document.body.classList.toggle("mobile-nav-open", narrowViewport && sidebarOpen);
});
</script>
