<template>
  <VnetPage
    eyebrow="Maintenance Overview"
    title="维保总览"
    description="集中查看维保覆盖、临期风险、自采项目和无维保缺口，优先处理影响生产连续性的风险队列。"
  >
    <template #actions>
      <el-button type="primary" @click="$router.push('/maintenance/upload')">上传合同 / 订单</el-button>
      <el-button @click="$router.push('/maintenance/assets')">查看设备台账</el-button>
    </template>

    <div class="vnet-metric-grid">
      <VnetMetricCard
        v-for="metric in dashboardMetrics"
        :key="metric.label"
        :label="metric.label"
        :value="metric.value"
        :note="metric.note"
        :tone="metric.tone"
      />
    </div>

    <VnetPanel title="快捷筛选" description="用 VNET 的 ElSearch 语义承载常用筛选；当前工程暂用 ElForm 组合实现。">
      <el-form :inline="true" :model="filters" class="vnet-search-form">
        <el-form-item label="机房">
          <el-select v-model="filters.dataCenter" clearable placeholder="全部机房">
            <el-option v-for="item in dataCenterOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业">
          <el-select v-model="filters.profession" clearable placeholder="全部专业">
            <el-option v-for="item in professionOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="到期状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="正常" value="normal" />
            <el-option label="临期" value="warning" />
            <el-option label="缺口" value="gap" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </VnetPanel>

    <div class="dashboard-grid">
      <VnetPanel title="风险队列" description="按临期、缺口和已过期优先级集中展示。">
        <el-table :data="riskRows" stripe>
          <el-table-column prop="assetCode" label="设备编码" min-width="130" />
          <el-table-column prop="assetName" label="设备名称" min-width="160" />
          <el-table-column prop="dataCenter" label="机房" min-width="150" />
          <el-table-column prop="profession" label="专业" width="90" />
          <el-table-column prop="owner" label="责任人" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <VnetStatusTag :status="row.status" />
            </template>
          </el-table-column>
        </el-table>
      </VnetPanel>

      <VnetPanel title="机房覆盖热力" description="以机房 + 专业维度查看覆盖状态，缺口区域优先补证据。">
        <div class="coverage-heatmap">
          <div v-for="item in heatmapRows" :key="item.name" class="heatmap-row">
            <span>{{ item.name }}</span>
            <div class="heatmap-cells">
              <span
                v-for="profession in professionOptions"
                :key="profession"
                class="heatmap-cell"
                :class="`heatmap-cell--${item.status[profession]}`"
              >
                {{ profession }}
              </span>
            </div>
          </div>
        </div>
      </VnetPanel>
    </div>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, reactive } from "vue";
import VnetMetricCard from "../../components/vnet/VnetMetricCard.vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import VnetStatusTag from "../../components/vnet/VnetStatusTag.vue";
import {
  assetRows,
  dashboardMetrics,
  dataCenterOptions,
  professionOptions,
  type CoverageStatus
} from "../../data/maintenance";

const filters = reactive({
  dataCenter: "",
  profession: "",
  status: ""
});

const riskRows = computed(() => assetRows.filter((row) => row.status !== "normal"));

const heatmapRows: Array<{ name: string; status: Record<string, CoverageStatus> }> = [
  {
    name: "亦庄A1机房",
    status: { 暖通: "normal", 弱电: "normal", 强电: "normal", 交付: "warning", 消防: "normal" }
  },
  {
    name: "亦庄A2机房",
    status: { 暖通: "warning", 弱电: "warning", 强电: "normal", 交付: "normal", 消防: "normal" }
  },
  {
    name: "廊坊B1机房",
    status: { 暖通: "normal", 弱电: "normal", 强电: "gap", 交付: "normal", 消防: "warning" }
  },
  {
    name: "通州C3机房",
    status: { 暖通: "normal", 弱电: "gap", 强电: "gap", 交付: "normal", 消防: "warning" }
  }
];

function resetFilters(): void {
  filters.dataCenter = "";
  filters.profession = "";
  filters.status = "";
}
</script>

<style scoped>
.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(360px, 0.8fr);
  gap: 16px;
}

.coverage-heatmap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.heatmap-row {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
}

.heatmap-row > span {
  color: var(--app-text-muted);
  font-size: 13px;
  font-weight: 600;
}

.heatmap-cells {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.heatmap-cell {
  display: grid;
  place-items: center;
  min-height: 34px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.heatmap-cell--normal {
  background: rgba(16, 127, 75, 0.12);
  color: #107f4b;
}

.heatmap-cell--warning {
  background: rgba(196, 107, 0, 0.14);
  color: #a85b00;
}

.heatmap-cell--gap,
.heatmap-cell--expired {
  background: rgba(201, 52, 27, 0.12);
  color: #b12d18;
}

@media (max-width: 1180px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
