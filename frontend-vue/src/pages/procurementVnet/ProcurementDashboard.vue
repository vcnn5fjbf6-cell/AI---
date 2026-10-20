<template>
  <ProcurementExecutiveDashboard
    v-if="viewMode === 'executive'"
    @open-custom="viewMode = 'custom'"
  />
  <ProcurementCustomDashboard
    v-else
    @back="viewMode = 'executive'"
  />
</template>

<script setup lang="ts">
import { ref } from "vue";
import ProcurementCustomDashboard from "./ProcurementCustomDashboard.vue";
import ProcurementExecutiveDashboard from "./ProcurementExecutiveDashboard.vue";

const viewMode = ref<"executive" | "custom">("executive");
</script>
