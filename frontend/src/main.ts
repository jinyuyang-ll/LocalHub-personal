import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import Home from './pages/Home.vue'
import Login from './pages/Login.vue'
import Orders from './pages/Orders.vue'
import Reservations from './pages/Reservations.vue'
import AiChat from './pages/AiChat.vue'
import ShopDetail from './pages/ShopDetail.vue'
import Blog from './pages/Blog.vue'
import UserCenter from './pages/UserCenter.vue'
import './styles.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: Home },
    { path: '/shops/:id', component: ShopDetail },
    { path: '/blogs', component: Blog },
    { path: '/me', component: UserCenter },
    { path: '/login', component: Login },
    { path: '/orders', component: Orders },
    { path: '/reservations', component: Reservations },
    { path: '/ai', component: AiChat }
  ]
})

createApp(App).use(router).mount('#app')
