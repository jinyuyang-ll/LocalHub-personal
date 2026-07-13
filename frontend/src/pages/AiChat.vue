<template>
  <section class="panel">
    <h1>AI 客服</h1>
    <textarea v-model="message" placeholder="请输入你的问题"></textarea>
    <button @click="send">发送</button>
    <pre>{{ answer }}</pre>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { api, unwrap } from '../api'

const message = ref('')
const answer = ref('')

async function send() {
  answer.value = unwrap<string>(await api.post('/ai/chat', { message: message.value }))
}
</script>
