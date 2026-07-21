import type { RouteRecordRaw } from 'vue-router'

/**
 * SME 固定资产路由
 * 资产类别、资产卡片、折旧计提、资产处置、资产盘点
 */
const routes: RouteRecordRaw[] = [
  // ─── 资产类别 ───
  {
    path: 'asset/category',
    name: 'AssetCategoryList',
    component: () => import('@/views/asset/category/AssetCategoryList.vue'),
    meta: { title: '资产类别', permission: 'asset:category:list', keepAlive: true },
  },

  // ─── 资产卡片 ───
  {
    path: 'asset/card',
    name: 'AssetCardList',
    component: () => import('@/views/asset/card/AssetCardList.vue'),
    meta: { title: '资产卡片', permission: 'asset:card:list', keepAlive: true },
  },

  // ─── 折旧计提 ───
  {
    path: 'asset/depreciation',
    name: 'AssetDepreciationView',
    component: () => import('@/views/asset/depreciation/AssetDepreciationView.vue'),
    meta: { title: '折旧计提', permission: 'asset:depreciation:run', keepAlive: true },
  },

  // ─── 资产处置 ───
  {
    path: 'asset/disposal',
    name: 'AssetDisposalList',
    component: () => import('@/views/asset/disposal/AssetDisposalList.vue'),
    meta: { title: '资产处置', permission: 'asset:disposal:list', keepAlive: true },
  },

  // ─── 资产盘点 ───
  {
    path: 'asset/inventory',
    name: 'AssetInventoryList',
    component: () => import('@/views/asset/inventory/AssetInventoryList.vue'),
    meta: { title: '资产盘点', permission: 'asset:inventory:list', keepAlive: true },
  },
]

export default routes