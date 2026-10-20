<template>
  <div class="page-card">
    <h2 class="page-title">工作台</h2>
    <el-row :gutter="12">
      <el-col :span="6" v-for="card in cards" :key="card.title">
        <el-card>
          <div style="font-size: 13px; color: #909399">{{ card.title }}</div>
          <div style="font-size: 26px; font-weight: 600; margin-top: 10px">{{ card.value }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
const cards = [
  { title: "待审批单据", value: 16 },
  { title: "进行中询价", value: 9 },
  { title: "本月采购金额(万元)", value: 382 },
  { title: "异常交付单", value: 3 }
];
</script>
