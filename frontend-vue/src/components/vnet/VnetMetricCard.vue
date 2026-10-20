<template>
  <el-card class="vnet-metric-card" :class="`vnet-metric-card--${tone || 'primary'}`" shadow="never">
    <div class="vnet-metric-label">{{ label }}</div>
    <div class="vnet-metric-value">{{ value }}</div>
  </el-card>
</template>

<script setup lang="ts">
defineProps<{
  label: string;
  value: string;
  note?: string;
  tone?: "primary" | "success" | "warning" | "danger";
}>();
</script>
