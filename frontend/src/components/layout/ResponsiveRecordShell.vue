<script setup>
defineProps({
  /** Accessible label for the desktop table region */
  desktopLabel: { type: String, default: 'Data table' },
});
</script>

<template>
  <div class="responsive-records">
    <div class="responsive-records__desktop" role="region" :aria-label="desktopLabel">
      <slot name="desktop" />
    </div>
    <div class="responsive-records__mobile" role="region" aria-label="Card list">
      <slot name="mobile" />
    </div>
  </div>
</template>

<style scoped>
.responsive-records__desktop {
  display: none;
}

.responsive-records__mobile {
  display: block;
}

@media (min-width: 900px) {
  .responsive-records__desktop {
    display: block;
  }

  .responsive-records__mobile {
    display: none;
  }
}
</style>
