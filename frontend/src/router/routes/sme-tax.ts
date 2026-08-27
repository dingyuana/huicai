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

  // ─── P58 发票-收付款勾稽（三流合一） ───
  {
    path: 'tax/invoice-reconcile',
    name: 'InvoiceReconcileView',
    component: () => import('@/views/tax/InvoiceReconcileView.vue'),
    meta: { title: '发票勾稽', permission: 'tax:input:list', keepAlive: true },
  },
]

export default routes