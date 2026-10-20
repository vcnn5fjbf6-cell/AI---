<template>
  <div class="page-card">
    <h2 class="page-title">采购申请</h2>
    <el-space style="margin-bottom: 12px">
      <el-button type="primary">新建申请</el-button>
      <el-button>导出</el-button>
    </el-space>
    <el-table :data="rows" border>
      <el-table-column prop="reqNo" label="申请单号" width="180" />
      <el-table-column prop="title" label="需求主题" min-width="220" />
      <el-table-column prop="region" label="区域" width="120" />
      <el-table-column prop="amount" label="金额(元)" width="140" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="180">
        <template #default>
          <el-button type="primary" link>详情</el-button>
          <el-button type="primary" link>提交审批</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
const rows = [
  { reqNo: "PR20260402001", title: "机房网络设备采购", region: "华北", amount: 780000, status: "待审批", createdAt: "2026-04-02 09:15" },
  { reqNo: "PR20260401007", title: "IDC备件补货", region: "华东", amount: 168000, status: "询价中", createdAt: "2026-04-01 14:06" }
];
</script>
