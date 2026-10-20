<template>
  <el-select
    :model-value="modelValue"
    filterable
    :disabled="disabled"
    :placeholder="placeholder"
    style="width: 100%"
    @update:model-value="handleUpdate"
  >
    <el-option-group v-for="group in PROCUREMENT_UNIT_GROUPS" :key="group.label" :label="group.label">
      <el-option v-for="option in group.options" :key="option.value" :label="option.label" :value="option.value" />
    </el-option-group>
  </el-select>
</template>

<script setup lang="ts">
import { PROCUREMENT_UNIT_GROUPS } from "../../data/measurement-units";

withDefaults(
  defineProps<{
    modelValue: string;
    placeholder?: string;
    disabled?: boolean;
  }>(),
  {
    placeholder: "请选择单位",
    disabled: false
  }
);

const emit = defineEmits<{
  (event: "update:modelValue", value: string): void;
}>();

function handleUpdate(value: string): void {
  emit("update:modelValue", value);
}
</script>
