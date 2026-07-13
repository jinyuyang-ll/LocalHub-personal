<template>
  <section>
    <h1>附近商户</h1>
    <div class="search">
      <input v-model="keyword" placeholder="搜索店铺名称" />
      <button @click="loadShops">搜索</button>
    </div>
    <article v-for="shop in shops" :key="shop.id" class="card">
      <h3>{{ shop.name }}</h3>
      <p>{{ shop.address }}</p>
      <button @click="loadVouchers(shop.id)">查看优惠券</button>
    </article>
    <section v-if="vouchers.length">
      <h2>优惠券</h2>
      <article v-for="voucher in vouchers" :key="voucher.id" class="card">
        <strong>{{ voucher.title }}</strong>
        <p>{{ voucher.subTitle }}</p>
        <button @click="receive(voucher.id)">领取</button>
        <button v-if="voucher.type === 1" @click="seckill(voucher.id)">秒杀</button>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { api, unwrap } from '../api'

const keyword = ref('')
const shops = ref<any[]>([])
const vouchers = ref<any[]>([])

async function loadShops() {
  shops.value = unwrap(await api.get('/shops/search', { params: { name: keyword.value } })) as any[]
}

async function loadVouchers(shopId: number) {
  vouchers.value = unwrap(await api.get(`/vouchers/shops/${shopId}`)) as any[]
}

async function receive(voucherId: number) {
  const orderId = unwrap(await api.post(`/vouchers/${voucherId}/receive`))
  alert(`领取成功，订单号：${orderId}`)
}

async function seckill(voucherId: number) {
  const orderId = unwrap(await api.post(`/seckill-vouchers/${voucherId}/orders`))
  alert(`秒杀请求已提交，订单号：${orderId}`)
}

loadShops()
</script>
