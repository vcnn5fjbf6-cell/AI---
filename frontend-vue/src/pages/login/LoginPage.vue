<template>
  <div class="login-page">
    <section class="login-shell">
      <div class="login-intro">
        <div class="brand-row">
          <div class="brand-logo">运</div>
          <div>
            <h1>酒仙桥区域运维平台</h1>
          </div>
        </div>
      </div>

      <div class="login-actions">
        <div class="login-mode-row">
          <el-button
            class="feishu-btn"
            size="large"
            :loading="feishuLoading || callbackLoading"
            @click="handleFeishuLogin"
          >
            <span class="feishu-logo">飞</span>
            <span>{{ callbackLoading ? "正在完成飞书登录" : "使用飞书登录" }}</span>
          </el-button>

          <button
            class="account-toggle"
            :class="{ active: passwordVisible }"
            type="button"
            :aria-pressed="passwordVisible"
            @click="passwordVisible = !passwordVisible"
          >
            账号登录
          </button>
        </div>
        <div class="login-note">请使用已绑定系统邮箱或手机号的企业飞书账号登录。</div>

        <el-form
          v-if="passwordVisible"
          ref="formRef"
          :model="formModel"
          :rules="rules"
          size="large"
          class="login-form"
          @keyup.enter="handlePasswordLogin"
        >
          <el-form-item prop="username">
            <el-input v-model="formModel.username" placeholder="请输入账号 / 姓名 / 工号" clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="formModel.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="password-btn" :loading="passwordLoading" @click="handlePasswordLogin">
              进入工作台
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </section>

    <el-dialog
      v-model="identityDialogVisible"
      title="选择登录身份"
      width="420px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="identity-dialog-body">
        <p>检测到多个同名账号，请确认本次进入工作台使用的身份。</p>
        <el-radio-group v-model="selectedCandidateId" class="identity-list">
          <el-radio
            v-for="candidate in loginCandidates"
            :key="candidate.userId"
            class="identity-option"
            :label="candidate.userId"
          >
            <span class="identity-name">{{ candidate.realName || candidate.username }}</span>
            <span class="identity-meta">工号：{{ candidate.employeeNo || "-" }}</span>
            <span class="identity-meta">部门：{{ candidate.deptName || "-" }}</span>
          </el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="identityDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordLoading" @click="handleCandidateConfirm">确认进入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { getFeishuLoginUrlApi } from "../../api/auth";
import { useAuthStore } from "../../stores/auth";
import type { LoginCandidate } from "../../types/auth";
import { resolveAccessHomePath, resolveInternalRedirect } from "../../utils/access-home";

const FEISHU_REDIRECT_KEY = "enterprise-platform-feishu-redirect";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const formRef = ref<FormInstance>();
const feishuLoading = ref(false);
const callbackLoading = ref(false);
const passwordLoading = ref(false);
const passwordVisible = ref(false);
const identityDialogVisible = ref(false);
const loginCandidates = ref<LoginCandidate[]>([]);
const selectedCandidateId = ref<number>();

const formModel = reactive({
  username: "",
  password: ""
});

const rules: FormRules<typeof formModel> = {
  username: [{ required: true, message: "请输入账号 / 姓名 / 工号", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }]
};

onMounted(() => {
  const code = queryValue(route.query.code);
  const state = queryValue(route.query.state);
  if (code && state) {
    void handleFeishuCallback(code, state);
  }
});

async function handleFeishuLogin(): Promise<void> {
  feishuLoading.value = true;
  try {
    const redirect = resolveRequestedRedirectPath();
    if (redirect) {
      sessionStorage.setItem(FEISHU_REDIRECT_KEY, redirect);
    } else {
      sessionStorage.removeItem(FEISHU_REDIRECT_KEY);
    }
    const result = await getFeishuLoginUrlApi(buildRedirectUri());
    window.location.href = result.url;
  } finally {
    feishuLoading.value = false;
  }
}

async function handleFeishuCallback(code: string, state: string): Promise<void> {
  callbackLoading.value = true;
  try {
    await authStore.loginByFeishu({
      code,
      state,
      redirectUri: buildRedirectUri()
    });
    const redirect = resolveInternalRedirect(sessionStorage.getItem(FEISHU_REDIRECT_KEY));
    sessionStorage.removeItem(FEISHU_REDIRECT_KEY);
    await router.replace(redirect || resolveAccessHomePath(authStore.appCodes, authStore.effectiveRoleCodes));
  } catch {
    ElMessage.error("飞书登录未完成，请重新发起登录");
    await router.replace({ path: "/login" });
  } finally {
    callbackLoading.value = false;
  }
}

async function handlePasswordLogin(): Promise<void> {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }

  passwordLoading.value = true;
  try {
    const result = await authStore.login({
      username: formModel.username,
      password: formModel.password
    });
    if (result.requiresUserSelection) {
      loginCandidates.value = result.candidates ?? [];
      selectedCandidateId.value = loginCandidates.value[0]?.userId;
      identityDialogVisible.value = loginCandidates.value.length > 0;
      if (!identityDialogVisible.value) {
        ElMessage.error("未找到可选择的登录身份");
      }
      return;
    }
    await router.replace(resolveRedirectPath());
  } finally {
    passwordLoading.value = false;
  }
}

async function handleCandidateConfirm(): Promise<void> {
  if (!selectedCandidateId.value) {
    ElMessage.warning("请选择登录身份");
    return;
  }
  passwordLoading.value = true;
  try {
    await authStore.login({
      username: formModel.username,
      password: formModel.password,
      selectedUserId: selectedCandidateId.value
    });
    identityDialogVisible.value = false;
    await router.replace(resolveRedirectPath());
  } finally {
    passwordLoading.value = false;
  }
}

function buildRedirectUri(): string {
  return `${window.location.origin}/login`;
}

function resolveRedirectPath(): string {
  return resolveRequestedRedirectPath() || resolveAccessHomePath(authStore.appCodes, authStore.effectiveRoleCodes);
}

function resolveRequestedRedirectPath(): string {
  return resolveInternalRedirect(route.query.redirect);
}

function queryValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}
</script>

<style scoped>
.login-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 20px;
  background:
    linear-gradient(135deg, rgba(18, 96, 232, 0.08), rgba(5, 150, 105, 0.08)),
    var(--app-bg);
}

.login-shell {
  width: min(480px, 100%);
  padding: 30px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.login-intro {
  margin-bottom: 26px;
}

.brand-row {
  display: flex;
  gap: 14px;
  align-items: center;
}

.brand-logo {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: var(--app-radius-sm);
  background: var(--app-primary);
  color: #ffffff;
  font-size: 20px;
  font-weight: 700;
}

.login-intro h1 {
  margin: 0;
  color: var(--app-text);
  font-size: 25px;
  line-height: 1.25;
}

.login-actions {
  display: grid;
  gap: 12px;
}

.login-mode-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.feishu-btn {
  width: 100%;
  height: 48px;
  border: 0;
  background: #1f74ff;
  color: #ffffff;
  font-weight: 700;
}

.feishu-btn:hover,
.feishu-btn:focus {
  background: #1665df;
  color: #ffffff;
}

.feishu-logo {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  margin-right: 8px;
  border-radius: 6px;
  background: #ffffff;
  color: #1f74ff;
  font-size: 14px;
  font-weight: 800;
}

.account-toggle {
  width: 100%;
  height: 48px;
  border: 1px solid rgba(31, 116, 255, 0.3);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
  color: var(--app-primary);
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  transition:
    background 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease;
}

.account-toggle:hover,
.account-toggle:focus,
.account-toggle.active {
  border-color: var(--app-primary);
  background: rgba(31, 116, 255, 0.08);
  color: #1665df;
}

.login-note {
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
}

.login-form {
  padding-top: 6px;
}

.password-btn {
  width: 100%;
}

.identity-dialog-body {
  display: grid;
  gap: 14px;
}

.identity-dialog-body p {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 14px;
  line-height: 1.6;
}

.identity-list {
  display: grid;
  gap: 10px;
  width: 100%;
}

.identity-option {
  height: auto;
  margin: 0;
  padding: 12px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius-sm);
  background: #f8fbff;
}

.identity-option :deep(.el-radio__label) {
  display: grid;
  min-width: 0;
  gap: 4px;
  padding-left: 10px;
}

.identity-name {
  color: var(--app-text);
  font-weight: 700;
}

.identity-meta {
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.4;
}

@media (max-width: 520px) {
  .login-shell {
    padding: 24px;
  }

  .login-intro h1 {
    font-size: 22px;
  }
}
</style>
