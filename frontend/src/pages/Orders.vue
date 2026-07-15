<template>
  <section class="panel">
    <h1>订单查询</h1>
    <input v-model="orderId" placeholder="订单号" />
    <button @click="query">查询状态</button>
    <button @click="startPolling" :disabled="polling">{{ polling ? `轮询中（${attempts}）` : '自动轮询' }}</button>
    <button v-if="polling" @click="stopPolling">停止</button>
    <p v-if="error" class="error">{{ error }}</p>
    <pre>{{ result }}</pre>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, unwrap } from '../api'

const orderId = ref('')
const result = ref('')
const polling = ref(false)
const attempts = ref(0)
const error = ref('')
let timer: number | undefined
const route = useRoute()

async function query() {
  if (!orderId.value) throw new Error('请输入订单号')
  const status = unwrap(await api.get(`/voucher-orders/${orderId.value}/status`))
  result.value = JSON.stringify(status, null, 2)
  return status
}

async function poll() {
  try {
    attempts.value += 1
    const status = await query()
    if (status !== 'PROCESSING' || attempts.value >= 30) stopPolling()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '查询失败'
    stopPolling()
  }
}

function startPolling() {
  stopPolling()
  polling.value = true
  attempts.value = 0
  error.value = ''
  timer = window.setInterval(poll, 1000)
  void poll()
}

function stopPolling() {
  polling.value = false
  if (timer) window.clearInterval(timer)
  timer = undefined
}

onBeforeUnmount(stopPolling)
onMounted(() => {
  if (typeof route.query.orderId === 'string') orderId.value = route.query.orderId
  if (route.query.poll === '1' && orderId.value) startPolling()
})
</script>
