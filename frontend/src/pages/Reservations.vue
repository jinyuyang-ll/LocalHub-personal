<template>
  <section class="panel">
    <h1>预约</h1>
    <input v-model="shopId" placeholder="店铺 ID" />
    <input v-model="reserveTime" placeholder="预约时间，如 2026-07-13T19:00:00" />
    <input v-model="remark" placeholder="备注" />
    <button @click="create">创建预约</button>
    <button @click="loadMine">我的预约</button>
    <article v-for="item in reservations" :key="item.id" class="card">
      <pre>{{ item }}</pre>
      <button @click="cancel(item.id)">取消</button>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { api, unwrap } from '../api'

const shopId = ref('')
const reserveTime = ref('')
const remark = ref('')
const reservations = ref<any[]>([])

async function create() {
  const id = unwrap(await api.post('/reservations', {
    shopId: Number(shopId.value),
    reserveTime: reserveTime.value,
    remark: remark.value
  }))
  alert(`预约成功：${id}`)
}

async function loadMine() {
  reservations.value = unwrap(await api.get('/reservations/me')) as any[]
}

async function cancel(id: number) {
  await api.post(`/reservations/${id}/cancel`)
  await loadMine()
}
</script>
