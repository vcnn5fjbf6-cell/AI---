<template>
  <div class="page-card">
    <h2 class="page-title">审批中心</h2>
    <el-table :data="rows" border>
      <el-table-column prop="bizNo" label="单据号" width="180" />
      <el-table-column prop="bizType" label="业务类型" width="120" />
      <el-table-column prop="submitter" label="提交人" width="120" />
      <el-table-column prop="region" label="区域" width="120" />
      <el-table-column prop="amount" label="金额(元)" width="140" />
      <el-table-column prop="waitingDays" label="待办时长" width="120" />
      <el-table-column label="操作" width="220">
        <template #default>
          <el-button type="success" link>通过</el-button>
          <el-button type="warning" link>退回</el-button>
          <el-button type="danger" link>驳回</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
const rows = [
  { bizNo: "PR20260402001", bizType: "采购申请", submitter: "张三", region: "华北", amount: 780000, waitingDays: "1天" },
  { bizNo: "BID20260401003", bizType: "定标审批", submitter: "李四", region: "华东", amount: 320000, waitingDays: "3小时" }
];
</script>
