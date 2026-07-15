<template>
  <section class="panel">
    <h1>AI 客服</h1>
    <div class="chat-history" aria-live="polite">
      <article v-for="(item, index) in history" :key="index" :class="['bubble', item.role]">
        <strong>{{ item.role === 'user' ? '我' : 'LocalHub' }}</strong>
        <p>{{ item.content }}</p>
      </article>
    </div>
    <textarea v-model="message" placeholder="请输入你的问题" @keydown.ctrl.enter="send"></textarea>
    <button @click="send" :disabled="streaming || !message.trim()">{{ streaming ? '回答中…' : '流式发送' }}</button>
    <button v-if="streaming" class="secondary" @click="stop">停止生成</button>
    <button v-if="history.length" class="secondary" @click="history = []">清空记录</button>
    <p v-if="error" class="error">{{ error }}</p>
  </section>

  <section class="panel">
    <h2>AI 预约助手</h2>
    <p>写操作需要先预览，再由你明确确认。确认令牌 5 分钟有效且只能使用一次。</p>
    <input v-model="shopId" placeholder="店铺 ID" />
    <input v-model="reserveTime" type="datetime-local" />
    <input v-model="remark" placeholder="备注" />
    <button @click="preview" :disabled="!shopId || !reserveTime">预览预约</button>
    <article v-if="previewData" class="card">
      <p>店铺：{{ previewData.shopName }}</p><p>时间：{{ previewData.reserveTime }}</p><p>备注：{{ previewData.remark || '无' }}</p>
      <button @click="confirm">确认创建</button>
      <button class="secondary" @click="previewData = undefined">取消</button>
    </article>
    <p v-if="reservationResult" class="success">{{ reservationResult }}</p>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { api, streamSse, unwrap } from '../api'

interface Preview { confirmationToken: string; shopName: string; reserveTime: string; remark?: string }
interface ChatMessage { role: 'user' | 'assistant'; content: string }
const message = ref('')
const history = ref<ChatMessage[]>([])
const error = ref('')
const streaming = ref(false)
const shopId = ref('')
const reserveTime = ref('')
const remark = ref('')
const previewData = ref<Preview>()
const reservationResult = ref('')
let controller: AbortController | undefined

async function send() {
  const question = message.value.trim()
  if (!question) return
  history.value.push({ role: 'user', content: question }, { role: 'assistant', content: '' })
  const answer = history.value[history.value.length - 1]
  message.value = ''; error.value = ''; streaming.value = true; controller = new AbortController()
  try {
    await streamSse(`/ai/chat/stream?message=${encodeURIComponent(question)}`, chunk => answer.content += chunk, controller.signal)
  } catch (e) {
    if (!(e instanceof DOMException && e.name === 'AbortError')) error.value = e instanceof Error ? e.message : '请求失败'
  } finally {
    streaming.value = false; controller = undefined
  }
}

function stop() { controller?.abort() }

async function preview() {
  previewData.value = unwrap<Preview>(await api.post('/ai/reservations/preview', {
    shopId: Number(shopId.value), reserveTime: reserveTime.value, remark: remark.value
  }))
  reservationResult.value = ''
}

async function confirm() {
  if (!previewData.value) return
  const id = unwrap<number>(await api.post('/ai/reservations/confirm', { confirmationToken: previewData.value.confirmationToken }))
  reservationResult.value = `预约创建成功，编号：${id}`
  previewData.value = undefined
}
</script>
