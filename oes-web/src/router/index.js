import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/LoginView.vue')
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/dashboard/DashboardView.vue'),
    meta: { title: '实时看板' }
  },
  {
    path: '/samples',
    name: 'Samples',
    component: () => import('../views/sample/SampleListView.vue'),
    meta: { title: '样本管理' }
  },
  {
    path: '/results',
    name: 'Results',
    component: () => import('../views/result/ResultReviewView.vue'),
    meta: { title: '结果审核' }
  },
  {
    path: '/instruments',
    name: 'Instruments',
    component: () => import('../views/instrument/InstrumentView.vue'),
    meta: { title: '仪器管理' }
  },
  {
    path: '/system/users',
    name: 'Users',
    component: () => import('../views/system/UserManageView.vue'),
    meta: { title: '用户管理' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
