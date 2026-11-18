<script setup lang="ts">
import AuthShell from '../components/auth/AuthShell.vue'
</script>

<template>
  <AuthShell title="找回密码" subtitle="密码重置请联系管理员处理">
    <div class="auth-form">
      <p class="auth-hint">
        为保障账号安全，忘记密码时请联系系统管理员协助重置。
      </p>
    </div>

    <p class="auth-alt">想起密码了？<RouterLink to="/login">返回登录</RouterLink></p>
  </AuthShell>
</template>
