<template>
  <VnetPage
    eyebrow="Asset Coverage"
    title="设备维保台账"
    description="按设备颗粒度展示维保覆盖关系，支持机房、专业、来源、到期状态和责任人组合筛选。"
  >
    <VnetPanel title="组合筛选" description="机房与人员选择后续可替换为 ElSelectMaDataCenter / ElSelectMaPerson。">
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
        <el-form-item label="维保来源">
          <el-select v-model="filters.sourceType" clearable placeholder="全部来源">
            <el-option label="总部框架" value="framework" />
            <el-option label="框架订单" value="order" />
            <el-option label="机房自采" value="local" />
            <el-option label="无维保" value="gap" />
          </el-select>
        </el-form-item>
        <el-form-item label="到期状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="正常" value="normal" />
            <el-option label="临期" value="warning" />
            <el-option label="已过期" value="expired" />
            <el-option label="缺口" value="gap" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任人">
          <el-select v-model="filters.owner" clearable placeholder="全部责任人">
            <el-option v-for="item in personOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </VnetPanel>

    <VnetPanel title="台账表格" :description="`当前筛选命中 ${filteredRows.length} 台设备。`">
      <el-table :data="pagedRows" stripe>
        <el-table-column prop="assetCode" label="设备编码" min-width="130" />
        <el-table-column prop="assetName" label="设备名称" min-width="160" />
        <el-table-column prop="dataCenter" label="机房" min-width="150" />
        <el-table-column prop="building" label="楼栋" width="90" />
        <el-table-column prop="profession" label="专业" width="90" />
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <VnetStatusTag :source-type="row.sourceType" />
          </template>
        </el-table-column>
        <el-table-column prop="sourceNo" label="来源编号" min-width="130" />
        <el-table-column prop="owner" label="责任人" width="90" />
        <el-table-column prop="endDate" label="服务结束" width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <VnetStatusTag :status="row.status" />
          </template>
        </el-table-column>
      </el-table>
      <div class="vnet-pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="filteredRows.length"
          :current-page="pageNo"
          :page-size="pageSize"
          @current-change="pageNo = $event"
        />
      </div>
    </VnetPanel>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import VnetStatusTag from "../../components/vnet/VnetStatusTag.vue";
import {
  assetRows,
  dataCenterOptions,
  personOptions,
  professionOptions,
  type CoverageStatus,
  type SourceType
} from "../../data/maintenance";

const pageNo = ref(1);
const pageSize = 10;

const filters = reactive({
  dataCenter: "",
  profession: "",
  sourceType: "" as SourceType | "",
  status: "" as CoverageStatus | "",
  owner: ""
});

const filteredRows = computed(() =>
  assetRows.filter((row) => {
    const matchRoom = !filters.dataCenter || row.dataCenter === filters.dataCenter;
    const matchProfession = !filters.profession || row.profession === filters.profession;
    const matchType = !filters.sourceType || row.sourceType === filters.sourceType;
    const matchStatus = !filters.status || row.status === filters.status;
    const matchOwner = !filters.owner || row.owner === filters.owner;
    return matchRoom && matchProfession && matchType && matchStatus && matchOwner;
  })
);

const pagedRows = computed(() => {
  const start = (pageNo.value - 1) * pageSize;
  return filteredRows.value.slice(start, start + pageSize);
});

function resetFilters(): void {
  filters.dataCenter = "";
  filters.profession = "";
  filters.sourceType = "";
  filters.status = "";
  filters.owner = "";
  pageNo.value = 1;
}
</script>
