<template>
  <el-card class="vnet-panel" shadow="never">
    <div v-if="title || $slots.actions" class="vnet-panel-head">
      <div>
        <h3 v-if="title">{{ title }}</h3>
      </div>
      <div v-if="$slots.actions" class="vnet-panel-actions">
        <slot name="actions" />
      </div>
    </div>
    <slot />
  </el-card>
</template>

<script setup lang="ts">
defineProps<{
  title?: string;
  description?: string;
}>();
</script>
