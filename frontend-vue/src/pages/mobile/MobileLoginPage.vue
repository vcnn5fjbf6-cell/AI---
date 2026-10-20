<template>
  <div class="mobile-login-page">
    <div class="mobile-login-shell">
      <div class="mobile-brand">
        <div class="mobile-brand-mark">酒</div>
        <h1>酒仙桥区域运维平台</h1>
        <p>手机登录终端</p>
      </div>

      <div class="mobile-login-card">
        <el-form label-position="top" @keyup.enter="handleLogin">
          <el-form-item>
            <el-input v-model="username" placeholder="账号 / 工号 / 姓名" :prefix-icon="User" size="large" clearable />
          </el-form-item>
          <el-form-item>
            <el-input v-model="password" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" size="large" />
          </el-form-item>
          <div class="mobile-login-options">
            <el-checkbox v-model="remember">记住账号</el-checkbox>
          </div>
          <el-button class="mobile-login-btn" type="primary" size="large" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form>
        <div class="mobile-login-tip">首次登录后请及时修改密码</div>
      </div>

      <div class="mobile-login-footer">企业采购 / 维保协同平台</div>
    </div>

    <el-dialog v-model="candidateVisible" title="选择登录身份" width="92%" append-to-body>
      <div class="candidate-list">
        <div
          v-for="candidate in candidates"
          :key="candidate.userId"
          class="candidate-item"
          :class="{ active: selectedCandidateId === candidate.userId }"
          @click="selectedCandidateId = candidate.userId"
        >
          <strong>{{ candidate.realName || candidate.username }}</strong>
          <span>工号：{{ candidate.employeeNo || "-" }} · 部门：{{ candidate.deptName || "-" }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="candidateVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="confirmCandidate">确认进入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Lock, User } from "@element-plus/icons-vue";
import { useAuthStore } from "../../stores/auth";
import type { LoginCandidate } from "../../types/auth";
import { resolveAccessHomePath, resolveInternalRedirect } from "../../utils/access-home";

const REMEMBER_KEY = "mobile-login-username";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const username = ref("");
const password = ref("");
const remember = ref(true);
const loading = ref(false);
const candidateVisible = ref(false);
const candidates = ref<LoginCandidate[]>([]);
const selectedCandidateId = ref<number>();

onMounted(() => {
  const saved = localStorage.getItem(REMEMBER_KEY);
  if (saved) {
    username.value = saved;
  }
  if (authStore.isLoggedIn) {
    void router.replace(resolveAccessHomePath(authStore.appCodes, authStore.effectiveRoleCodes));
  }
});

async function handleLogin(): Promise<void> {
  if (!username.value.trim() || !password.value) {
    ElMessage.warning("请输入账号和密码");
    return;
  }
  if (remember.value && username.value.trim()) {
    localStorage.setItem(REMEMBER_KEY, username.value.trim());
  } else {
    localStorage.removeItem(REMEMBER_KEY);
  }
  loading.value = true;
  try {
    if (authStore.loginWithHrLocalUser(username.value.trim(), password.value)) {
      await redirectAfterLogin();
      return;
    }
    const result = await authStore.login({
      username: username.value.trim(),
      password: password.value
    });
    if (result.requiresUserSelection) {
      candidates.value = result.candidates ?? [];
      selectedCandidateId.value = candidates.value[0]?.userId;
      candidateVisible.value = candidates.value.length > 0;
      if (!candidateVisible.value) {
        ElMessage.error("未找到可选择的登录身份");
      }
      return;
    }
    await redirectAfterLogin();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "登录失败");
  } finally {
    loading.value = false;
  }
}

async function confirmCandidate(): Promise<void> {
  if (!selectedCandidateId.value) {
    ElMessage.warning("请选择登录身份");
    return;
  }
  loading.value = true;
  try {
    await authStore.login({
      username: username.value.trim(),
      password: password.value,
      selectedUserId: selectedCandidateId.value
    });
    candidateVisible.value = false;
    await redirectAfterLogin();
  } finally {
    loading.value = false;
  }
}

async function redirectAfterLogin(): Promise<void> {
  if (authStore.token.startsWith("hr-local-")) {
    await router.replace("/hr/attendance");
    return;
  }
  const redirect = resolveInternalRedirect(route.query.redirect);
  await router.replace(redirect || resolveAccessHomePath(authStore.appCodes, authStore.effectiveRoleCodes));
}
</script>

<style scoped>
.mobile-login-page {
  display: flex;
  min-height: 100vh;
  min-height: 100dvh;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: linear-gradient(160deg, #eff6ff 0%, #ffffff 55%, #f0fdf9 100%);
  box-sizing: border-box;
}

.mobile-login-shell {
  width: min(420px, 100%);
}

.mobile-brand {
  margin-bottom: 26px;
  text-align: center;
}

.mobile-brand-mark {
  display: grid;
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  place-items: center;
  border-radius: 16px;
  background: var(--app-primary);
  color: #ffffff;
  font-size: 24px;
  font-weight: 800;
  box-shadow: 0 12px 28px rgba(22, 93, 255, 0.22);
}

.mobile-brand h1 {
  margin: 0;
  color: var(--app-text);
  font-size: 22px;
  font-weight: 800;
  line-height: 1.35;
}

.mobile-brand p {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.mobile-login-card {
  padding: 24px 20px;
  border: 1px solid var(--app-line);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.1);
}

.mobile-login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: -2px 0 14px;
}

.mobile-login-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 700;
}

.mobile-login-tip {
  margin-top: 14px;
  color: var(--app-text-soft);
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
}

.mobile-login-footer {
  margin-top: 22px;
  color: var(--app-text-soft);
  font-size: 12px;
  text-align: center;
}

.candidate-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.candidate-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--app-line);
  border-radius: 8px;
  cursor: pointer;
}

.candidate-item.active {
  border-color: var(--app-primary);
  background: var(--app-primary-soft);
}

.candidate-item span {
  color: var(--app-text-muted);
  font-size: 12px;
}
</style>