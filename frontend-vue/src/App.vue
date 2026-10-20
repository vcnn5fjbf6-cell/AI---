<template>
  <div v-if="testEnvironment" class="test-environment-badge" role="status">
    测试环境 · {{ buildRef }}
  </div>
  <router-view />
</template>

<script setup lang="ts">
import { isTestEnvironment } from "./config/runtime-environment";

const testEnvironment = isTestEnvironment();
const buildRef = import.meta.env.VITE_BUILD_REF?.trim() || "local";
</script>

<style scoped>
.test-environment-badge {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 9999;
  padding: 6px 10px;
  border: 1px solid rgb(251 146 60 / 70%);
  border-radius: 999px;
  color: #9a3412;
  background: rgb(255 247 237 / 94%);
  box-shadow: 0 6px 18px rgb(124 45 18 / 12%);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  pointer-events: none;
}
</style>
