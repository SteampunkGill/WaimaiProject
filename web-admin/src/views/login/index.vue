<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { UserFilled, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)

const form = ref({
  username: 'admin',
  password: 'admin123'
})

async function handleLogin() {
  loading.value = true
  try {
    const res: any = await request.post('/auth/login/admin', {
      username: form.value.username,
      password: form.value.password
    })
    localStorage.setItem('accessToken', res.data.accessToken)
    localStorage.setItem('refreshToken', res.data.refreshToken)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch {
    ElMessage.error('账号或密码错误')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- Left Brand Panel -->
    <div class="login-brand">
      <div class="brand-overlay"></div>
      <div class="brand-content">
        <div class="brand-icon">🍔</div>
        <h1 class="brand-title">外卖配送管理平台</h1>
        <p class="brand-desc">一站式管理商家、骑手、订单与AI推荐</p>
        <div class="brand-features">
          <div class="bf-item">
            <div class="bf-icon">📊</div>
            <div>
              <div class="bf-title">数据看板</div>
              <div class="bf-sub">实时营收与订单趋势</div>
            </div>
          </div>
          <div class="bf-item">
            <div class="bf-icon">✅</div>
            <div>
              <div class="bf-title">审核管理</div>
              <div class="bf-sub">商家与骑手入驻审核</div>
            </div>
          </div>
          <div class="bf-item">
            <div class="bf-icon">🤖</div>
            <div>
              <div class="bf-title">AI分析</div>
              <div class="bf-sub">智能推荐效果追踪</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Right Login Form -->
    <div class="login-form-panel">
      <div class="login-card">
        <div class="login-header">
          <h2>管理员登录</h2>
          <p>请使用管理员账号登录系统</p>
        </div>

        <el-form @submit.prevent="handleLogin" class="login-form">
          <el-form-item>
            <el-input
              v-model="form.username"
              placeholder="管理员账号"
              size="large"
              :prefix-icon="UserFilled"
              clearable
            />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              size="large"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="login-footer">
          <span class="demo-hint">默认账号: admin / admin123</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  overflow: hidden;
}

/* Left Brand */
.login-brand {
  flex: 1;
  background: linear-gradient(135deg, #1a1f36 0%, #2d3a5c 50%, #3b5998 100%);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.brand-overlay {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 30%, rgba(64,158,255,0.15) 0%, transparent 50%),
    radial-gradient(circle at 80% 70%, rgba(103,194,58,0.1) 0%, transparent 50%);
}
.brand-content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 60px;
  max-width: 420px;
}
.brand-icon {
  font-size: 64px;
  margin-bottom: 20px;
}
.brand-title {
  font-size: var(--font-size-h1);
  color: var(--color-white);
  font-weight: var(--font-weight-bold);
  margin: 0 0 12px;
  letter-spacing: 2px;
}
.brand-desc {
  font-size: var(--font-size-body);
  color: rgba(255,255,255,0.6);
  margin: 0 0 48px;
}

.brand-features {
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.bf-item {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #fff;
}
.bf-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: rgba(255,255,255,0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  backdrop-filter: blur(10px);
}
.bf-title {
  font-size: var(--font-size-body);
  font-weight: var(--font-weight-semibold);
}
.bf-sub {
  font-size: var(--font-size-caption);
  color: rgba(255,255,255,0.55);
  margin-top: 2px;
}

/* Right Form */
.login-form-panel {
  width: 460px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  padding: 60px;
}
.login-card {
  width: 100%;
}
.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.login-header h2 {
  font-size: var(--font-size-h2);
  color: var(--color-text-primary);
  margin: 0 0 var(--spacing-sm);
  font-weight: var(--font-weight-bold);
}
.login-header p {
  font-size: var(--font-size-body);
  color: var(--color-text-placeholder);
  margin: 0;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: none;
  border: 1px solid var(--color-border-dark);
  transition: all 0.2s;
  padding: 4px 12px;
}
.login-form :deep(.el-input__wrapper:hover) {
  border-color: var(--color-primary);
}
.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(24,144,255,0.08);
}

.login-btn {
  width: 100%;
  height: 46px;
  border-radius: 10px;
  font-size: var(--font-size-h5);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 4px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  border: none;
}
.login-btn:hover {
  background: linear-gradient(135deg, var(--color-primary-light), var(--color-primary));
}

.login-footer {
  text-align: center;
}
.demo-hint {
  font-size: var(--font-size-caption);
  color: var(--color-text-disabled);
}
</style>
