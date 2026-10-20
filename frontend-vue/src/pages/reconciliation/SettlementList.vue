<template>
  <div class="page-card">
    <h2 class="page-title">对账结算</h2>
    <el-table :data="rows" border>
      <el-table-column prop="recNo" label="对账单号" width="180" />
      <el-table-column prop="supplier" label="供应商" min-width="200" />
      <el-table-column prop="invoiceNo" label="发票号" width="180" />
      <el-table-column prop="amount" label="应付金额(元)" width="150" />
      <el-table-column prop="matchStatus" label="三单匹配" width="120" />
      <el-table-column prop="status" label="结算状态" width="120" />
      <el-table-column label="操作" width="180">
        <template #default>
          <el-button type="primary" link>查看</el-button>
          <el-button type="primary" link>发起付款</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
const rows = [
  { recNo: "REC20260401004", supplier: "上海某系统有限公司", invoiceNo: "031001900211", amount: 296000, matchStatus: "通过", status: "待付款" },
  { recNo: "REC20260329012", supplier: "深圳某科技有限公司", invoiceNo: "044002300178", amount: 128000, matchStatus: "差异", status: "处理中" }
];
</script>
