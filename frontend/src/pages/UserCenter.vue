<template>
  <section>
    <h1>用户中心</h1>
    <p v-if="error" class="error">{{ error }}</p>
    <article v-if="summary" class="panel">
      <h2>{{ summary.user?.nickName || 'LocalHub 用户' }}</h2>
      <p>连续签到：{{ summary.continuousSignDays }} 天</p>
      <button @click="sign">今日签到</button>
    </article>
    <h2>最近订单与优惠券</h2>
    <article v-for="order in summary?.orders || []" :key="order.id" class="card">
      <strong>订单 {{ order.id }}</strong><p>优惠券 {{ order.voucherId }} · 状态 {{ order.status }}</p>
    </article>
    <h2>我的预约</h2>
    <article v-for="item in summary?.reservations || []" :key="item.id" class="card">
      <strong>店铺 {{ item.shopId }}</strong><p>{{ item.reserveTime }} · 状态 {{ item.status }}</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { api, unwrap } from '../api'
const summary = ref<any>(); const error = ref('')
async function load() { try { summary.value = unwrap(await api.get('/users/me/summary')) } catch(e) { error.value=e instanceof Error?e.message:'加载失败' } }
async function sign() { await api.post('/users/me/sign'); await load() }
load()
</script>
