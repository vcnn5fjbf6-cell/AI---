<template>
  <div class="page-card">
    <h2 class="page-title">用户与角色</h2>
    <el-table :data="rows" border>
      <el-table-column prop="username" label="账号" width="160" />
      <el-table-column prop="name" label="姓名" width="120" />
      <el-table-column prop="role" label="角色" width="140" />
      <el-table-column prop="region" label="区域" width="120" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="160">
        <template #default>
          <el-button type="primary" link>编辑</el-button>
          <el-button type="primary" link>授权</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
const rows = [
  { username: "admin", name: "系统管理员", role: "管理员", region: "全局", status: "启用" },
  { username: "buyer_hb", name: "王采购", role: "采购员", region: "华北", status: "启用" }
];
</script>
