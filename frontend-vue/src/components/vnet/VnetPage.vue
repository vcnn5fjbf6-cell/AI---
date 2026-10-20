<template>
  <div class="vnet-page">
    <section class="vnet-page-head">
      <div class="vnet-page-copy">
        <h2>{{ title }}</h2>
      </div>
      <div v-if="$slots.actions" class="vnet-page-actions">
        <slot name="actions" />
      </div>
    </section>

    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  description?: string;
  eyebrow?: string;
}>();
</script>
