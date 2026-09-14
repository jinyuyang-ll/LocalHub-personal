<template>
  <section>
    <p><router-link to="/">← 返回首页</router-link></p>
    <p v-if="loading">正在加载商家详情…</p>
    <p v-if="error" class="error">{{ error }}</p>
    <article v-if="shop" class="panel">
      <span class="cache-badge">GET /shops/{{ shop.id }} · Caffeine → Redis → MySQL</span>
      <h1>{{ shop.name }}</h1>
      <p>{{ shop.address }}</p>
      <p>人均：¥{{ formatPrice(shop.avgPrice) }}</p>
      <p>营业时间：{{ shop.openHours || '以门店为准' }}</p>
      <button @click="toReservation">预约到店</button>
      <button class="secondary" @click="router.push('/ai')">咨询 AI 客服</button>
    </article>
    <h2>可用优惠券</h2>
    <article v-for="voucher in vouchers" :key="voucher.id" class="card">
      <strong>{{ voucher.title }}</strong>
      <p>{{ voucher.subTitle }}</p>
      <button v-if="voucher.type === 1" @click="seckill(voucher.id)">立即秒杀</button>
      <button v-else @click="receive(voucher.id)">领取优惠券</button>
    </article>
    <p v-if="!loading && !vouchers.length">暂无优惠券</p>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, unwrap } from '../api'

const route = useRoute(); const router = useRouter()
const shop = ref<any>(); const vouchers = ref<any[]>([])
const loading = ref(true); const error = ref('')
const shopId = Number(route.params.id)

function formatPrice(value?: number) { return value == null ? '-' : (value / 100).toFixed(2) }
function toReservation() { router.push({ path: '/reservations', query: { shopId: String(shopId) } }) }
async function receive(id: number) { alert(`领取成功，订单号：${unwrap(await api.post(`/vouchers/${id}/receive`))}`) }
async function seckill(id: number) {
  const orderId = unwrap(await api.post(`/seckill-vouchers/${id}/orders`))
  await router.push({ path: '/orders', query: { orderId: String(orderId), poll: '1' } })
}
onMounted(async () => {
  try {
    ;[shop.value, vouchers.value] = await Promise.all([
      api.get(`/shops/${shopId}`).then(unwrap), api.get(`/vouchers/shops/${shopId}`).then(unwrap)
    ]) as any
  } catch (e) { error.value = e instanceof Error ? e.message : '详情加载失败' }
  finally { loading.value = false }
})
</script>
