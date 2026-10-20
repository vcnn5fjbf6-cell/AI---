<template>
  <div class="page-card">
    <h2 class="page-title">流程配置</h2>
    <el-alert title="支持按采购类型、金额阈值、区域自动选择流程模板" type="info" :closable="false" />
    <el-table :data="rows" border style="margin-top: 12px">
      <el-table-column prop="name" label="流程名称" min-width="220" />
      <el-table-column prop="condition" label="触发条件" min-width="260" />
      <el-table-column prop="nodes" label="审批节点" min-width="220" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="140">
        <template #default>
          <el-button type="primary" link>编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
const rows = [
  { name: "常规采购审批", condition: "金额 <= 200000", nodes: "经理 -> 采购", status: "启用" },
  { name: "大额采购审批", condition: "金额 > 200000", nodes: "经理 -> 区域经理 -> 采购", status: "启用" }
];
</script>
