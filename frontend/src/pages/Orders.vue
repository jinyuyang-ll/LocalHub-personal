<template>
  <section class="panel">
    <h1>订单查询</h1>
    <input v-model="orderId" placeholder="订单号" />
    <button @click="query">查询状态</button>
    <pre>{{ result }}</pre>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { api, unwrap } from '../api'

const orderId = ref('')
const result = ref('')

async function query() {
  result.value = JSON.stringify(unwrap(await api.get(`/voucher-orders/${orderId.value}/status`)), null, 2)
}
</script>
