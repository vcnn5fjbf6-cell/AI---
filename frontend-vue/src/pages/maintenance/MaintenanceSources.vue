<template>
  <VnetPage
    eyebrow="Maintenance Sources"
    title="维保来源"
    description="管理总部框架、框架采购订单、机房自采项目和真实缺口，确保每条设备覆盖都有可追溯依据。"
  >
    <template #actions>
      <el-button type="primary" @click="$router.push('/maintenance/upload')">新增来源材料</el-button>
    </template>

    <VnetPanel title="来源筛选" description="机房筛选后续可替换为 VNET UI Business 的 ElSelectMaDataCenter。">
      <el-form :inline="true" :model="filters" class="vnet-search-form">
        <el-form-item label="机房">
          <el-select v-model="filters.dataCenter" clearable placeholder="全部机房">
            <el-option v-for="item in dataCenterOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="filters.sourceType" clearable placeholder="全部类型">
            <el-option label="总部框架" value="framework" />
            <el-option label="框架订单" value="order" />
            <el-option label="机房自采" value="local" />
            <el-option label="无维保" value="gap" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
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

    <VnetPanel title="框架 / 订单 / 自采来源列表" description="总部权限用户可查看所有合同和维保来源。">
      <el-table :data="filteredRows" stripe>
        <el-table-column prop="sourceNo" label="来源编号" min-width="140" />
        <el-table-column prop="sourceName" label="来源名称" min-width="220" />
        <el-table-column label="来源类型" width="110">
          <template #default="{ row }">
            <VnetStatusTag :source-type="row.sourceType" />
          </template>
        </el-table-column>
        <el-table-column prop="dataCenter" label="机房" min-width="150" />
        <el-table-column prop="owner" label="责任人" width="90" />
        <el-table-column prop="endDate" label="服务结束" width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <VnetStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </VnetPanel>

    <el-dialog v-model="detailVisible" title="维保来源详情" width="760px">
      <el-descriptions v-if="currentSource" :column="2" border>
        <el-descriptions-item label="来源编号">{{ currentSource.sourceNo }}</el-descriptions-item>
        <el-descriptions-item label="来源类型">{{ sourceTypeText(currentSource.sourceType) }}</el-descriptions-item>
        <el-descriptions-item label="来源名称" :span="2">{{ currentSource.sourceName }}</el-descriptions-item>
        <el-descriptions-item label="机房">{{ currentSource.dataCenter }}</el-descriptions-item>
        <el-descriptions-item label="片区">{{ currentSource.area }}</el-descriptions-item>
        <el-descriptions-item label="服务开始">{{ currentSource.startDate }}</el-descriptions-item>
        <el-descriptions-item label="服务结束">{{ currentSource.endDate }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ currentSource.owner }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ currentSource.amount }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import VnetStatusTag from "../../components/vnet/VnetStatusTag.vue";
import {
  dataCenterOptions,
  sourceRows,
  sourceTypeText,
  type CoverageStatus,
  type MaintenanceSource,
  type SourceType
} from "../../data/maintenance";

const filters = reactive({
  dataCenter: "",
  sourceType: "" as SourceType | "",
  status: "" as CoverageStatus | ""
});

const detailVisible = ref(false);
const currentSource = ref<MaintenanceSource | null>(null);

const filteredRows = computed(() =>
  sourceRows.filter((row) => {
    const matchRoom = !filters.dataCenter || row.dataCenter === filters.dataCenter;
    const matchType = !filters.sourceType || row.sourceType === filters.sourceType;
    const matchStatus = !filters.status || row.status === filters.status;
    return matchRoom && matchType && matchStatus;
  })
);

function resetFilters(): void {
  filters.dataCenter = "";
  filters.sourceType = "";
  filters.status = "";
}

function openDetail(row: MaintenanceSource): void {
  currentSource.value = row;
  detailVisible.value = true;
}
</script>
