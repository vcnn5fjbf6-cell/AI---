<template>
  <section class="page-hero">
    <div class="page-hero-copy">
      <div class="page-hero-heading">
        <div class="page-hero-text">
          <h2 class="page-hero-title">{{ title }}</h2>
        </div>
        <div v-if="$slots.actions" class="page-hero-actions">
          <slot name="actions" />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  description?: string;
  eyebrow?: string;
}>();
</script>
