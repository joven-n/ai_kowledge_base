import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      redirect: '/chat',
      children: [
        {
          path: 'chat',
          name: 'Chat',
          component: () => import('@/views/ChatView.vue'),
          meta: { title: 'AI对话', icon: 'MessageCircle' },
        },
        {
          path: 'knowledge',
          name: 'Knowledge',
          component: () => import('@/views/KnowledgeView.vue'),
          meta: { title: '知识库管理', icon: 'Folder' },
        },
        {
          path: 'system',
          name: 'System',
          component: () => import('@/views/SystemView.vue'),
          meta: { title: '系统监控', icon: 'Activity' },
        },
      ],
    },
  ],
})

export default router
