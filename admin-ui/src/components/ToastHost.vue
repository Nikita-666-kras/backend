<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const { items } = storeToRefs(toast)
</script>

<template>
  <div class="toast-host" aria-live="polite">
    <div v-for="item in items" :key="item.id" class="toast" :class="item.kind" @click="toast.dismiss(item.id)">
      {{ item.message }}
    </div>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  right: 1rem;
  bottom: 1rem;
  z-index: 1000;
  display: grid;
  gap: 0.45rem;
  width: min(360px, calc(100vw - 2rem));
}

.toast {
  padding: 0.75rem 0.9rem;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: rgba(8, 28, 52, 0.95);
  color: var(--ink);
  font-size: 0.9rem;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
}

.toast.ok {
  border-color: rgba(141, 198, 63, 0.45);
  color: #c8f08a;
}

.toast.error {
  border-color: rgba(255, 107, 107, 0.45);
  color: #ffc4c4;
}

.toast.info {
  border-color: rgba(157, 182, 255, 0.4);
  color: #d5e0ff;
}
</style>
