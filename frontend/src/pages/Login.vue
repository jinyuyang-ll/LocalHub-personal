<template>
  <section class="panel">
    <h1>登录</h1>
    <input v-model="phone" placeholder="手机号" />
    <div class="row">
      <input v-model="code" placeholder="验证码" />
      <button @click="sendCode">发送验证码</button>
    </div>
    <button @click="login">登录</button>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, unwrap } from '../api'

const phone = ref('')
const code = ref('')
const router = useRouter()

async function sendCode() {
  await api.post('/auth/code', null, { params: { phone: phone.value } })
  alert('验证码已发送，请查看后端日志/Redis')
}

async function login() {
  const token = unwrap<string>(await api.post('/auth/login', { phone: phone.value, code: code.value }))
  localStorage.setItem('token', token)
  await router.push('/')
}
</script>
