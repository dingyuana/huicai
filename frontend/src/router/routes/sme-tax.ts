import type { RouteRecordRaw } from 'vue-router'

/**
 * SME 税务发票路由
 * 进项发票、销项发票、增值税计算
 */
const routes: RouteRecordRaw[] = [
  // ─── 进项发票 ───
  {
    path: 'tax/input-invoice',
    name: 'InputInvoiceList',
    component: () => import('@/views/tax/input-invoice/InputInvoiceList.vue'),
    meta: { title: '进项发票', permission: 'tax:input:list', keepAlive: true },
  },

  // ─── 销项发票 ───
  {
    path: 'tax/output-invoice',
    name: 'OutputInvoiceList',
    component: () => import('@/views/tax/output-invoice/OutputInvoiceList.vue'),
    meta: { title: '销项发票', permission: 'tax:output:list', keepAlive: true },
  },

  // ─── 增值税计算 ───
  {
    path: 'tax/vat',
    name: 'TaxVatView',
    component: () => import('@/views/tax/declaration/TaxVatView.vue'),
    meta: { title: '增值税计算', permission: 'tax:vat:view', keepAlive: true },
  },
]

export default routes