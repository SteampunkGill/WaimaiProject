<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { computed, ref } from 'vue'

const router = useRouter()
const route = useRoute()

const isCollapse = ref(false)
const activeMenu = computed(() => route.path)

const menuItems = [
  { path: '/dashboard', title: '数据看板', icon: 'DataAnalysis' },
  { path: '/merchant/audit', title: '商家审核', icon: 'Shop' },
  { path: '/rider/audit', title: '骑手审核', icon: 'UserFilled' },
  { path: '/order/monitor', title: '订单监控', icon: 'List' },
  { path: '/order/overtime', title: '超时监控', icon: 'Clock' },
  { path: '/delivery/joint', title: '联合配送', icon: 'Connection' },
  { path: '/coupon/manage', title: '优惠券管理', icon: 'Ticket' },
  { path: '/dispute/manage', title: '纠纷处理', icon: 'Warning' },
  { path: '/system/config', title: '系统配置', icon: 'Setting' },
  { path: '/recommend/monitor', title: 'AI推荐分析', icon: 'MagicStick' },
]

function logout() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  router.push('/login')
}

const breadcrumb = computed(() => {
  const item = menuItems.find(m => m.path === route.path)
  return item ? item.title : ''
})
</script>

<template>
  <el-container class="layout-container">
    <!-- Sidebar -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo-area" :class="{ collapsed: isCollapse }">
        <div class="logo-icon">🍔</div>
        <span v-show="!isCollapse" class="logo-text">外卖管理后台</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        background-color="transparent"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#fff"
        router
        class="side-menu"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer" v-show="!isCollapse">
        <div class="admin-avatar">A</div>
        <div class="admin-info">
          <span class="admin-name">系统管理员</span>
          <span class="admin-role">超级管理员</span>
        </div>
      </div>
    </el-aside>

    <!-- Main -->
    <el-container class="main-container">
      <el-header class="main-header">
        <div class="header-left">
          <el-icon class="collapse-btn" :size="20" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="breadcrumb">{{ breadcrumb }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tooltip content="刷新页面" placement="bottom">
            <el-icon class="header-icon" :size="18" @click="router.go(0)"><Refresh /></el-icon>
          </el-tooltip>
          <el-button type="danger" text size="small" @click="logout">
            <el-icon><SwitchButton /></el-icon>
            <span>退出</span>
          </el-button>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout-container {
  height: 100vh;
  background: var(--color-bg-page);
}

/* Sidebar */
.layout-aside {
  background: linear-gradient(180deg, #1a1f36 0%, #1e2a3a 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.3s;
  border-right: 1px solid var(--color-border);
}

.logo-area {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 18px;
  gap: 10px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}
.logo-area.collapsed {
  justify-content: center;
  padding: 0;
}
.logo-icon {
  font-size: 22px;
  flex-shrink: 0;
}
.logo-text {
  font-size: var(--font-size-h5);
  font-weight: var(--font-weight-bold);
  color: var(--color-white);
  white-space: nowrap;
  letter-spacing: 1px;
}

.side-menu {
  flex: 1;
  border-right: none;
  padding: 8px 0;
}
.side-menu .el-menu-item {
  margin: 2px 8px;
  border-radius: 8px;
  min-height: 44px;
  line-height: 44px;
  transition: all 0.2s;
}
.side-menu .el-menu-item:hover {
  background: rgba(255,255,255,0.08) !important;
}
.side-menu .el-menu-item.is-active {
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)) !important;
  box-shadow: 0 4px 12px rgba(24,144,255,0.3);
}
:deep(.side-menu .el-menu-item.is-active .el-icon) {
  color: #fff;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(255,255,255,0.06);
  display: flex;
  align-items: center;
  gap: 10px;
}
.admin-avatar {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-light));
  color: var(--color-white);
  font-weight: var(--font-weight-bold);
  font-size: var(--font-size-h5);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.admin-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.admin-name {
  font-size: var(--font-size-body-sm);
  color: var(--color-white);
  font-weight: var(--font-weight-medium);
}
.admin-role {
  font-size: var(--font-size-caption-sm);
  color: rgba(255,255,255,0.5);
}

/* Header */
.main-header {
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-lg);
  height: 56px;
  box-shadow: var(--shadow-xs);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.collapse-btn {
  cursor: pointer;
  color: var(--color-text-secondary);
  padding: 6px;
  border-radius: var(--radius-sm);
  transition: all 0.2s;
}
.collapse-btn:hover {
  background: var(--color-bg-hover);
  color: var(--color-primary);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.header-icon {
  cursor: pointer;
  color: var(--color-text-placeholder);
  padding: 6px;
  border-radius: var(--radius-sm);
  transition: all 0.2s;
}
.header-icon:hover {
  background: var(--color-bg-hover);
  color: var(--color-primary);
}

/* Content */
.main-content {
  background: var(--color-bg-page);
  padding: var(--spacing-lg);
  min-height: 0;
}

/* Page transition */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.25s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
