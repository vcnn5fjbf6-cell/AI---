<template>
  <VnetPage title="系统更新">
    <template #actions>
      <div class="page-release-status">
        <span><el-icon><Calendar /></el-icon>{{ latestRelease.releaseDate }} 发布</span>
        <el-tag type="success" effect="light">生产已上线</el-tag>
      </div>
    </template>

    <section class="current-release" aria-label="当前生产版本">
      <div class="current-release-copy">
        <span class="current-label">当前生产版本</span>
        <div class="current-version-row">
          <h2>{{ CURRENT_SYSTEM_VERSION }}</h2>
          <el-tag type="success" effect="dark">已上线</el-tag>
        </div>
        <h3>{{ latestRelease.title }}</h3>
        <p>{{ latestRelease.summary }}</p>
      </div>

      <dl class="release-metrics">
        <div>
          <dt>收录版本</dt>
          <dd>{{ SYSTEM_UPDATE_RECORDS.length }}</dd>
        </div>
        <div>
          <dt>已修复问题</dt>
          <dd>{{ fixedIssueCount }}</dd>
        </div>
        <div>
          <dt>功能更新</dt>
          <dd>{{ featureCount }}</dd>
        </div>
        <div>
          <dt>体验优化</dt>
          <dd>{{ improvementCount }}</dd>
        </div>
      </dl>
    </section>

    <section class="release-browser" aria-label="版本发布记录">
      <header class="browser-toolbar">
        <div>
          <h2>版本记录</h2>
          <p>选择版本查看本次发布包含的修复、功能和优化。</p>
        </div>
        <el-segmented v-model="activeType" :options="filterOptions" />
      </header>

      <div v-if="filteredRecords.length > 0" class="browser-body">
        <nav class="version-nav" aria-label="版本目录">
          <button
            v-for="record in filteredRecords"
            :key="record.version"
            type="button"
            class="version-nav-item"
            :class="{ active: selectedRecord?.version === record.version }"
            @click="selectedVersion = record.version"
          >
            <span class="version-nav-copy">
              <span class="version-nav-label">
                {{ record.version === CURRENT_SYSTEM_VERSION ? "当前版本" : record.releaseDate }}
              </span>
              <strong>{{ record.version }}</strong>
              <span>{{ record.title }}</span>
            </span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </nav>

        <article v-if="selectedRecord" class="release-detail">
          <header class="detail-header">
            <div class="detail-title">
              <span>{{ selectedRecord.releaseDate }}</span>
              <h3>{{ selectedRecord.title }}</h3>
              <p>{{ selectedRecord.summary }}</p>
            </div>
            <div class="detail-version">
              <strong>{{ selectedRecord.version }}</strong>
              <span><el-icon><CircleCheckFilled /></el-icon>已正式发布</span>
            </div>
          </header>

          <div class="change-groups">
            <section
              v-for="group in selectedGroups"
              :key="group.type"
              class="change-group"
              :class="`change-group-${group.type.toLowerCase()}`"
            >
              <header class="change-group-header">
                <div class="change-group-title">
                  <span class="change-group-icon">
                    <el-icon>
                      <Tools v-if="group.type === 'FIX'" />
                      <Plus v-else-if="group.type === 'FEATURE'" />
                      <TrendCharts v-else />
                    </el-icon>
                  </span>
                  <div>
                    <h4>{{ group.label }}</h4>
                    <span>{{ group.changes.length }} 项</span>
                  </div>
                </div>
                <el-tag :type="group.tagType" effect="plain">{{ group.statusText }}</el-tag>
              </header>

              <div class="change-items">
                <div v-for="change in group.changes" :key="change.title" class="change-item">
                  <el-icon class="change-check"><CircleCheckFilled /></el-icon>
                  <div>
                    <strong>{{ change.title }}</strong>
                    <p>{{ change.description }}</p>
                  </div>
                </div>
              </div>
            </section>
          </div>
        </article>
      </div>

      <el-empty v-else description="暂无对应更新记录" />
    </section>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ArrowRight, Calendar, CircleCheckFilled, Plus, Tools, TrendCharts } from "@element-plus/icons-vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import {
  CURRENT_SYSTEM_VERSION,
  SYSTEM_UPDATE_RECORDS,
  type SystemUpdateChange,
  type SystemUpdateChangeType
} from "../../config/systemUpdates";

type UpdateFilter = "ALL" | SystemUpdateChangeType;

interface ChangeGroup {
  type: SystemUpdateChangeType;
  label: string;
  statusText: string;
  tagType: "success" | "primary" | "warning";
  changes: SystemUpdateChange[];
}

const filterOptions: Array<{ label: string; value: UpdateFilter }> = [
  { label: "全部", value: "ALL" },
  { label: "问题修复", value: "FIX" },
  { label: "功能更新", value: "FEATURE" },
  { label: "体验优化", value: "IMPROVEMENT" }
];

const typeMeta: Record<SystemUpdateChangeType, Omit<ChangeGroup, "type" | "changes">> = {
  FIX: { label: "问题修复", statusText: "全部已修复", tagType: "success" },
  FEATURE: { label: "功能更新", statusText: "全部已上线", tagType: "primary" },
  IMPROVEMENT: { label: "体验优化", statusText: "全部已上线", tagType: "warning" }
};

const activeType = ref<UpdateFilter>("ALL");
const selectedVersion = ref(CURRENT_SYSTEM_VERSION);
const latestRelease = SYSTEM_UPDATE_RECORDS[0];
const fixedIssueCount = countChanges("FIX");
const featureCount = countChanges("FEATURE");
const improvementCount = countChanges("IMPROVEMENT");

const filteredRecords = computed(() => {
  if (activeType.value === "ALL") {
    return SYSTEM_UPDATE_RECORDS;
  }
  return SYSTEM_UPDATE_RECORDS.filter((record) => record.changes.some((change) => change.type === activeType.value));
});

const selectedRecord = computed(() =>
  filteredRecords.value.find((record) => record.version === selectedVersion.value) || filteredRecords.value[0] || null
);

const selectedGroups = computed<ChangeGroup[]>(() => {
  if (!selectedRecord.value) {
    return [];
  }
  return (["FIX", "FEATURE", "IMPROVEMENT"] as SystemUpdateChangeType[])
    .filter((type) => activeType.value === "ALL" || activeType.value === type)
    .map((type) => ({
      type,
      ...typeMeta[type],
      changes: selectedRecord.value?.changes.filter((change) => change.type === type) || []
    }))
    .filter((group) => group.changes.length > 0);
});

watch(filteredRecords, (records) => {
  if (!records.some((record) => record.version === selectedVersion.value)) {
    selectedVersion.value = records[0]?.version || "";
  }
});

function countChanges(type: SystemUpdateChangeType): number {
  return SYSTEM_UPDATE_RECORDS.reduce(
    (total, record) => total + record.changes.filter((change) => change.type === type).length,
    0
  );
}
</script>

<style scoped>
.page-release-status {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.page-release-status > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.current-release {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(360px, 0.75fr);
  min-height: 230px;
  margin-bottom: 16px;
  overflow: hidden;
  border: 1px solid #bfd0eb;
  border-radius: var(--app-radius);
  background: var(--app-surface);
  box-shadow: 0 10px 24px rgba(22, 93, 255, 0.07);
}

.current-release-copy {
  padding: 28px 30px;
  border-top: 4px solid var(--app-primary);
}

.current-label {
  display: block;
  margin-bottom: 8px;
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 700;
}

.current-version-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.current-version-row h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 32px;
  line-height: 1.2;
}

.current-release-copy h3 {
  margin: 18px 0 8px;
  color: var(--app-text);
  font-size: 20px;
}

.current-release-copy p {
  max-width: 660px;
  margin: 0;
  color: var(--app-text-muted);
  font-size: 14px;
  line-height: 1.75;
}

.release-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  border-left: 1px solid var(--app-line-soft);
  background: #f8fbff;
}

.release-metrics > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 22px 24px;
  border-right: 1px solid var(--app-line-soft);
  border-bottom: 1px solid var(--app-line-soft);
}

.release-metrics > div:nth-child(2n) {
  border-right: 0;
}

.release-metrics > div:nth-child(n + 3) {
  border-bottom: 0;
}

.release-metrics dt {
  color: var(--app-text-muted);
  font-size: 13px;
}

.release-metrics dd {
  margin: 0;
  color: var(--app-text);
  font-size: 28px;
  font-weight: 750;
  line-height: 1;
}

.release-browser {
  overflow: hidden;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: var(--app-surface);
}

.browser-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--app-line);
}

.browser-toolbar h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 17px;
}

.browser-toolbar p {
  margin: 5px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.browser-body {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  min-height: 520px;
}

.version-nav {
  overflow-y: auto;
  border-right: 1px solid var(--app-line);
  background: #f8fafc;
}

.version-nav-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 18px;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 92px;
  padding: 15px 16px;
  border: 0;
  border-bottom: 1px solid var(--app-line-soft);
  background: transparent;
  color: var(--app-text-muted);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;
}

.version-nav-item:hover {
  background: #eef4ff;
}

.version-nav-item:active {
  transform: translateY(1px);
}

.version-nav-item:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: -2px;
}

.version-nav-item.active {
  background: #e8f0ff;
  color: var(--app-primary);
  box-shadow: inset 3px 0 0 var(--app-primary);
}

.version-nav-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.version-nav-label {
  color: var(--app-text-soft);
  font-size: 11px;
}

.version-nav-item.active .version-nav-label {
  color: #436da8;
}

.version-nav-copy strong {
  color: var(--app-text);
  font-size: 14px;
}

.version-nav-item.active .version-nav-copy strong {
  color: var(--app-primary);
}

.version-nav-copy > span:last-child {
  overflow: hidden;
  color: var(--app-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.release-detail {
  min-width: 0;
  padding: 26px 30px 34px;
}

.detail-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--app-line);
}

.detail-title > span {
  color: var(--app-text-soft);
  font-size: 12px;
}

.detail-title h3 {
  margin: 7px 0 8px;
  color: var(--app-text);
  font-size: 21px;
}

.detail-title p {
  max-width: 720px;
  margin: 0;
  color: var(--app-text-muted);
  font-size: 14px;
  line-height: 1.7;
}

.detail-version {
  display: grid;
  justify-items: end;
  gap: 8px;
}

.detail-version strong {
  color: var(--app-primary);
  font-size: 16px;
}

.detail-version span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--app-success);
  font-size: 12px;
}

.change-groups {
  display: grid;
}

.change-group {
  padding: 22px 0 4px;
  border-bottom: 1px solid var(--app-line-soft);
}

.change-group:last-child {
  border-bottom: 0;
}

.change-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.change-group-title {
  display: flex;
  align-items: center;
  gap: 11px;
}

.change-group-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 6px;
  background: var(--app-primary-soft);
  color: var(--app-primary);
  font-size: 17px;
}

.change-group-fix .change-group-icon {
  background: var(--app-success-soft);
  color: var(--app-success);
}

.change-group-improvement .change-group-icon {
  background: var(--app-warning-soft);
  color: var(--app-warning);
}

.change-group-title h4 {
  margin: 0;
  color: var(--app-text);
  font-size: 15px;
}

.change-group-title div > span {
  display: block;
  margin-top: 3px;
  color: var(--app-text-soft);
  font-size: 11px;
}

.change-items {
  padding-left: 45px;
}

.change-item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: start;
  gap: 10px;
  padding: 13px 0;
}

.change-check {
  margin-top: 2px;
  color: var(--app-success);
  font-size: 16px;
}

.change-item strong {
  color: var(--app-text);
  font-size: 14px;
}

.change-item p {
  margin: 5px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.65;
}

@media (max-width: 1000px) {
  .current-release {
    grid-template-columns: 1fr;
  }

  .release-metrics {
    border-top: 1px solid var(--app-line-soft);
    border-left: 0;
  }
}

@media (max-width: 820px) {
  .browser-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .browser-body {
    grid-template-columns: 1fr;
  }

  .version-nav {
    display: flex;
    overflow-x: auto;
    overflow-y: hidden;
    border-right: 0;
    border-bottom: 1px solid var(--app-line);
  }

  .version-nav-item {
    min-width: 210px;
    min-height: 86px;
    border-right: 1px solid var(--app-line-soft);
    border-bottom: 0;
  }

  .version-nav-item.active {
    box-shadow: inset 0 -3px 0 var(--app-primary);
  }

  .release-detail {
    padding: 24px 22px 30px;
  }
}

@media (max-width: 560px) {
  .page-release-status > span {
    display: none;
  }

  .current-release-copy {
    padding: 22px 20px;
  }

  .current-version-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .current-version-row h2 {
    font-size: 26px;
  }

  .release-metrics > div {
    padding: 18px;
  }

  .release-metrics dd {
    font-size: 24px;
  }

  .browser-toolbar {
    padding: 16px;
  }

  .browser-toolbar :deep(.el-segmented) {
    max-width: 100%;
    overflow-x: auto;
  }

  .detail-header {
    grid-template-columns: 1fr;
  }

  .detail-version {
    justify-items: start;
  }

  .change-items {
    padding-left: 0;
  }

  .change-group-header {
    align-items: flex-start;
  }
}
</style>
