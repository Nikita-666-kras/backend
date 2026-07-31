<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { downloadProposalPdf, buildKpPdfFilename, fetchAllProposals, type KpProposal } from '@/api/kp'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const items = ref<KpProposal[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    items.value = await fetchAllProposals()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось загрузить КП')
  } finally {
    loading.value = false
  }
}

async function onDownload(item: KpProposal) {
  try {
    if (item.status !== 'FINAL') {
      toast.error('Сначала сформируйте PDF (статус FINAL)')
      return
    }
    await downloadProposalPdf(item.id, buildKpPdfFilename(item.droneModelName, item.number))
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось скачать PDF')
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-header"><h1>КП · Архив</h1></header>
    <div class="card">
      <p v-if="loading" class="muted">Загрузка…</p>
      <table v-else class="table">
        <thead>
          <tr><th>№</th><th>Менеджер</th><th>Кому</th><th>Модель</th><th>Статус</th><th>Итог</th><th>PDF</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td>{{ item.number }}</td>
            <td>{{ item.managerUsername }}</td>
            <td>{{ item.recipient }}</td>
            <td>{{ item.droneModelName }}</td>
            <td>{{ item.status }}</td>
            <td>{{ item.grandTotal }}</td>
            <td><button class="btn secondary mini" type="button" @click="onDownload(item)">Скачать</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
