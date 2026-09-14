<template>
  <section>
    <div class="row"><h1>探店社区</h1><button @click="loadHot">热门</button><button class="secondary" @click="loadFeed">关注流</button></div>
    <p v-if="error" class="error">{{ error }}</p>
    <article v-for="blog in blogs" :key="blog.id" class="card">
      <h2>{{ blog.title }}</h2>
      <small>{{ blog.name || `用户 ${blog.userId}` }}</small>
      <p class="blog-content">{{ blog.content }}</p>
      <button @click="like(blog)">👍 {{ blog.liked || 0 }}</button>
      <button class="secondary" @click="toggleComments(blog)">评论 {{ blog.comments || 0 }}</button>
      <section v-if="activeBlog === blog.id" class="comments">
        <div class="row"><input v-model="comment" maxlength="255" placeholder="写下评论"/><button @click="submitComment(blog)">发布</button></div>
        <p v-for="item in comments" :key="item.id">用户 {{ item.userId }}：{{ item.content }}</p>
      </section>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { api, unwrap } from '../api'
const blogs = ref<any[]>([]); const comments = ref<any[]>([])
const activeBlog = ref<number>(); const comment = ref(''); const error = ref('')
async function loadHot() { try { blogs.value = unwrap(await api.get('/blogs/hot')) as any[] } catch(e) { fail(e) } }
async function loadFeed() {
  try {
    const data:any = unwrap(await api.get('/blogs/feed', { params: { lastId: Date.now(), offset: 0 } }))
    blogs.value = data?.list || []
  } catch(e) { fail(e) }
}
async function like(blog:any) { try { await api.post(`/blogs/${blog.id}/like`); await loadHot() } catch(e) { fail(e) } }
async function toggleComments(blog:any) {
  activeBlog.value = activeBlog.value === blog.id ? undefined : blog.id
  if (activeBlog.value) await loadComments(blog.id)
}
async function submitComment(blog:any) {
  if (!comment.value.trim()) return
  await api.post(`/blogs/${blog.id}/comments`, { content: comment.value.trim() })
  comment.value = ''; activeBlog.value = blog.id; await loadComments(blog.id)
}
async function loadComments(blogId:number) { comments.value = unwrap(await api.get(`/blogs/${blogId}/comments`)) as any[] }
function fail(e:unknown) { error.value = e instanceof Error ? e.message : '请求失败' }
loadHot()
</script>
