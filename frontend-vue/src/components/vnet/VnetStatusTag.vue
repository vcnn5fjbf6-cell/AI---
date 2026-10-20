<template>
  <el-tag :type="tagType" effect="light">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { CoverageStatus, SourceType } from "../../data/maintenance";
import { coverageStatusText, sourceTypeText } from "../../data/maintenance";

const props = defineProps<{
  status?: CoverageStatus;
  sourceType?: SourceType;
  priority?: "high" | "medium" | "low";
}>();

const label = computed(() => {
  if (props.status) return coverageStatusText(props.status);
  if (props.sourceType) return sourceTypeText(props.sourceType);
  if (props.priority === "high") return "高";
  if (props.priority === "medium") return "中";
  return "低";
});

const tagType = computed<"primary" | "success" | "warning" | "danger" | "info">(() => {
  if (props.status === "normal") return "success";
  if (props.status === "warning") return "warning";
  if (props.status === "expired" || props.status === "gap") return "danger";
  if (props.sourceType === "framework") return "primary";
  if (props.sourceType === "order") return "success";
  if (props.sourceType === "local") return "warning";
  if (props.sourceType === "gap") return "danger";
  if (props.priority === "high") return "danger";
  if (props.priority === "medium") return "warning";
  return "info";
});
</script>
