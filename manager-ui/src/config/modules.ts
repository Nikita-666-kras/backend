export interface HubModule {
  id: string
  title: string
  route: string
  group: 'main' | 'kp' | 'soon'
  enabled: boolean
  badge?: string
}

export const hubModules: HubModule[] = [
  { id: 'dashboard', title: 'Обзор', route: '/', group: 'main', enabled: true },
  { id: 'kp-new', title: 'Новый КП', route: '/kp/new', group: 'kp', enabled: true },
  { id: 'kp-list', title: 'Мои КП', route: '/kp', group: 'kp', enabled: true },
  { id: 'clients', title: 'Клиенты', route: '/clients', group: 'soon', enabled: false, badge: 'скоро' },
  { id: 'leads', title: 'Заявки', route: '/leads', group: 'soon', enabled: false, badge: 'скоро' },
  { id: 'orders', title: 'Заказы', route: '/orders', group: 'soon', enabled: false, badge: 'скоро' }
]

export const hubGroupLabels: Record<HubModule['group'], string> = {
  main: 'Главная',
  kp: 'КП',
  soon: 'Скоро'
}
