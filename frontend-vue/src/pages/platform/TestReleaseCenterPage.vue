<template>
  <VnetPage title="上线管理">
    <template #actions>
      <template v-if="canAccessPage">
        <el-button :icon="Refresh" @click="reloadFrame">刷新工作台</el-button>
        <el-button type="primary" plain @click="openReleaseConsole">新窗口打开</el-button>
      </template>
    </template>

    <el-result
      v-if="!canAccessPage"
      icon="warning"
      title="当前环境不可使用上线管理"
      sub-title="上线管理只在测试环境向平台超级管理员开放。"
    />

    <template v-else>
      <div class="release-shell">
        <div class="release-shell-head">
          <div>
            <div class="release-shell-title">
              <span class="environment-dot" aria-hidden="true"></span>
              测试环境发布控制台
            </div>
            <p>勾选已通过检查的更新，创建批次后进入组合测试。</p>
          </div>
          <div class="release-shell-meta">
            <el-tag type="info" effect="plain">凭据隔离</el-tag>
            <el-tag type="warning" effect="plain">需二次验证</el-tag>
          </div>
        </div>

        <iframe
          :key="frameKey"
          class="release-frame"
          :src="releaseConsoleUrl"
          title="上线管理安全工作台"
        />
      </div>
    </template>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import { isTestEnvironment } from "../../config/runtime-environment";
import { useAuthStore } from "../../stores/auth";

const testEnvironment = isTestEnvironment();
const authStore = useAuthStore();
const canAccessPage = computed(
  () => testEnvironment && authStore.effectiveRoleCodes.some((role) => role === "SUPER_ADMIN")
);

const releaseConsoleUrl = computed(() => {
  const configuredUrl = String(import.meta.env.VITE_RELEASE_CONTROL_URL || "").trim();
  if (configuredUrl) {
    return configuredUrl;
  }
  const protocol = window.location.protocol === "https:" ? "https:" : "http:";
  return `${protocol}//${window.location.hostname}:18081/`;
});
const frameKey = ref(0);

function reloadFrame(): void {
  frameKey.value += 1;
}

function openReleaseConsole(): void {
  const releaseWindow = window.open(releaseConsoleUrl.value, "_blank", "noopener,noreferrer");
  if (!releaseWindow) {
    ElMessage.warning("浏览器阻止了新窗口，请允许弹窗后重试");
  }
}
</script>

<style scoped>
.release-shell {
  overflow: hidden;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
}

.release-shell-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-line-soft);
  background: var(--app-surface-muted);
}

.release-shell-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 700;
}

.environment-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--app-success);
  box-shadow: 0 0 0 3px rgba(16, 127, 75, 0.12);
}

.release-shell-head p {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
}

.release-shell-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.release-frame {
  display: block;
  width: 100%;
  min-height: calc(100vh - 196px);
  height: 860px;
  border: 0;
  background: #ffffff;
}

@media (max-width: 760px) {
  .release-shell-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .release-shell-meta {
    justify-content: flex-start;
  }

  .release-frame {
    height: 760px;
  }
}
</style>
