<template>
  <div class="shell">
    <header class="nav">
      <router-link to="/">LocalHub</router-link>
      <nav>
        <router-link to="/login">登录</router-link>
        <router-link to="/orders">订单</router-link>
        <router-link to="/reservations">预约</router-link>
        <router-link to="/ai">AI 客服</router-link>
      </nav>
    </header>
    <main>
      <router-view />
    </main>
  </div>
</template>
