<template>
  <div class="page-card">
    <h2 class="page-title">采购订单</h2>
    <el-table :data="rows" border>
      <el-table-column prop="poNo" label="订单号" width="180" />
      <el-table-column prop="supplier" label="供应商" min-width="200" />
      <el-table-column prop="amount" label="订单金额(元)" width="160" />
      <el-table-column prop="deliveryDate" label="计划交付" width="140" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column label="操作" width="160">
        <template #default>
          <el-button type="primary" link>详情</el-button>
          <el-button type="primary" link>跟踪</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
const rows = [
  { poNo: "PO20260402006", supplier: "上海某系统有限公司", amount: 296000, deliveryDate: "2026-04-12", status: "待发货" },
  { poNo: "PO20260330021", supplier: "深圳某科技有限公司", amount: 128000, deliveryDate: "2026-04-05", status: "部分到货" }
];
</script>
