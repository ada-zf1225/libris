import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/ReaderLayout.vue'),
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/views/reader/HomeView.vue'),
        },
        {
          path: 'search',
          name: 'search',
          component: () => import('@/views/reader/SearchView.vue'),
        },
        {
          path: 'books/:id(\\d+)',
          name: 'book-detail',
          component: () => import('@/views/reader/BookDetailView.vue'),
        },
        {
          path: 'my',
          name: 'my-library',
          component: () => import('@/views/reader/MyLibraryView.vue'),
        },
      ],
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { adminOnly: true },
      children: [
        {
          path: '',
          name: 'admin-dashboard',
          component: () => import('@/views/admin/DashboardView.vue'),
        },
        {
          path: 'circulation',
          name: 'admin-circulation',
          component: () => import('@/views/admin/CirculationDeskView.vue'),
        },
        {
          path: 'books',
          name: 'admin-books',
          component: () => import('@/views/admin/BooksView.vue'),
        },
        {
          path: 'readers',
          name: 'admin-readers',
          component: () => import('@/views/admin/ReadersView.vue'),
        },
        {
          path: 'policies',
          name: 'admin-policies',
          component: () => import('@/views/admin/PoliciesView.vue'),
        },
        {
          path: 'logs',
          name: 'admin-logs',
          component: () => import('@/views/admin/LogsView.vue'),
        },
        {
          path: 'suggestions',
          name: 'admin-suggestions',
          component: () => import('@/views/admin/SuggestionsView.vue'),
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { public: true },
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.bootstrap()

  if (to.meta.public) {
    if (to.name === 'login' && auth.isAuthenticated) {
      return auth.isAdmin ? { name: 'admin-dashboard' } : { name: 'home' }
    }
    return true
  }
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.adminOnly && !auth.isAdmin) {
    return { name: 'home' }
  }
  return true
})

export default router
