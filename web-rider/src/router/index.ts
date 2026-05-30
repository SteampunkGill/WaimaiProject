import { createRouter, createWebHistory } from 'vue-router'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'Login', component: () => import('@/views/login/index.vue'), meta: { title: '骑手登录' } },
    { path: '/', name: 'Home', component: () => import('@/views/home/index.vue'), meta: { title: '接单大厅', requireAuth: true } },
    { path: '/tasks', name: 'Tasks', component: () => import('@/views/task/list.vue'), meta: { title: '我的任务', requireAuth: true } },
    { path: '/joint', name: 'JointDelivery', component: () => import('@/views/joint/list.vue'), meta: { title: '联合配送', requireAuth: true } },
    { path: '/income', name: 'Income', component: () => import('@/views/income/index.vue'), meta: { title: '收入', requireAuth: true } },
    { path: '/track/:orderNo', name: 'Track', component: () => import('@/views/track/index.vue'), meta: { title: '配送导航', requireAuth: true } },
    { path: '/register', name: 'Register', component: () => import('@/views/register/index.vue'), meta: { title: '骑手注册' } },
  ]
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('accessToken')
  if (to.meta.requireAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/')
  } else {
    next()
  }
})

export default router
