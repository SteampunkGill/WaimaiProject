import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/index.vue'),
      meta: { noAuth: true }
    },
    {
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/index.vue'),
          meta: { title: '数据看板', icon: 'DataAnalysis' }
        },
        {
          path: 'merchant/audit',
          name: 'MerchantAudit',
          component: () => import('@/views/merchant/audit.vue'),
          meta: { title: '商家审核', icon: 'Shop' }
        },
        {
          path: 'rider/audit',
          name: 'RiderAudit',
          component: () => import('@/views/rider/audit.vue'),
          meta: { title: '骑手审核', icon: 'UserFilled' }
        },
        {
          path: 'order/monitor',
          name: 'OrderMonitor',
          component: () => import('@/views/order/monitor.vue'),
          meta: { title: '订单监控', icon: 'List' }
        },
        {
          path: 'recommend/monitor',
          name: 'RecommendMonitor',
          component: () => import('@/views/recommend/monitor.vue'),
          meta: { title: 'AI推荐分析', icon: 'MagicStick' }
        },
        {
          path: 'coupon/manage',
          name: 'CouponManage',
          component: () => import('@/views/coupon/manage.vue'),
          meta: { title: '优惠券管理', icon: 'Ticket' }
        },
        {
          path: 'dispute/manage',
          name: 'DisputeManage',
          component: () => import('@/views/dispute/manage.vue'),
          meta: { title: '纠纷处理', icon: 'Warning' }
        },
        {
          path: 'system/config',
          name: 'SystemConfig',
          component: () => import('@/views/system/config.vue'),
          meta: { title: '系统配置', icon: 'Setting' }
        },
        {
          path: 'order/overtime',
          name: 'OrderOvertime',
          component: () => import('@/views/order/overtime.vue'),
          meta: { title: '超时监控', icon: 'Clock' }
        },
        {
          path: 'delivery/joint',
          name: 'JointDelivery',
          component: () => import('@/views/delivery/joint.vue'),
          meta: { title: '联合配送', icon: 'Connection' }
        }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  if (to.meta.noAuth) {
    next()
    return
  }
  const token = localStorage.getItem('accessToken')
  if (!token) {
    next('/login')
    return
  }
  next()
})

export default router
