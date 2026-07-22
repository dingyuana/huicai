<template>
  <div :class="['sidebar', { collapsed: appStore.sidebarCollapsed }]">
    <div class="sidebar-logo">
      <div v-if="!appStore.sidebarCollapsed" class="logo-content">
        <span class="logo-brand">慧财财务</span>
      </div>
      <span v-else class="logo-icon">慧</span>
    </div>
    <el-menu
      :default-active="route.path"
      :collapse="appStore.sidebarCollapsed"
      :router="true"
      class="sidebar-menu"
    >
      <!-- 首页 -->
      <el-menu-item index="/dashboard">
        <el-icon><HomeFilled /></el-icon>
        <template #title>首页</template>
      </el-menu-item>

      <!-- 基础数据 -->
      <el-sub-menu index="basis">
        <template #title>
          <el-icon><Notebook /></el-icon>
          <span>基础数据</span>
        </template>
        <el-menu-item index="/basis/account-and-summary">科目摘要</el-menu-item>
        <el-menu-item index="/basis/period">会计期间</el-menu-item>
        <el-menu-item index="/basis/party">客商档案</el-menu-item>
        <el-menu-item index="/system/classification-rule">分类规则</el-menu-item>
        <el-sub-menu index="system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统参数</span>
          </template>
          <el-menu-item index="/basis/config">系统参数</el-menu-item>
          <el-menu-item index="/system/clear-data">数据维护</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/finance/voucher-setup?tab=type">凭证类型</el-menu-item>
        <el-menu-item index="/finance/bank-account">银行账户</el-menu-item>
      </el-sub-menu>

      <!-- 财务核心 -->
      <el-sub-menu index="finance">
        <template #title>
          <el-icon><Coin /></el-icon>
          <span>财务核心</span>
        </template>
        <el-menu-item index="/finance/voucher">凭证管理</el-menu-item>
        <el-menu-item index="/finance/voucher-setup?tab=template">凭证模板</el-menu-item>
        <el-menu-item index="/finance/ledger">账簿查询</el-menu-item>
        <el-menu-item index="/finance/period-close">期末结账</el-menu-item>
        <el-menu-item index="/finance/beginning-balance">期初建账</el-menu-item>
        <el-menu-item index="/finance/carryover-guide">结转向导</el-menu-item>
      </el-sub-menu>

      <!-- 业务单据 -->
      <el-sub-menu index="business">
        <template #title>
          <el-icon><Document /></el-icon>
          <span>业务单据</span>
        </template>
        <el-menu-item index="/finance/business-doc">业务单据</el-menu-item>
        <el-menu-item index="/finance/bank-journal">银行日记账</el-menu-item>
        <el-menu-item index="/finance/bank-statement">银行对账单</el-menu-item>
        <el-menu-item index="/finance/bank-reconciliation">银行对账</el-menu-item>
        <el-menu-item index="/finance/cash-journal">现金日记账</el-menu-item>
        <el-menu-item index="/finance/ticket">票据管理</el-menu-item>
        <el-menu-item index="/arap/reconciliation-workbench">核销工作台</el-menu-item>
        <el-menu-item index="/finance/business-doc?tab=expense">费用报销</el-menu-item>
      </el-sub-menu>

      <!-- 税务发票 -->
      <el-sub-menu index="tax">
        <template #title>
          <el-icon><Ticket /></el-icon>
          <span>税务发票</span>
        </template>
        <el-menu-item index="/tax/input-invoice">进项发票</el-menu-item>
        <el-menu-item index="/tax/output-invoice">销项发票</el-menu-item>
        <el-menu-item index="/tax/vat">增值税计算</el-menu-item>
      </el-sub-menu>

      <!-- 固定资产 -->
      <el-sub-menu index="asset">
        <template #title>
          <el-icon><Box /></el-icon>
          <span>固定资产</span>
        </template>
        <el-menu-item index="/asset/category">资产类别</el-menu-item>
        <el-menu-item index="/asset/card">资产卡片</el-menu-item>
        <el-menu-item index="/asset/depreciation">折旧计提</el-menu-item>
        <el-menu-item index="/asset/disposal">资产处置</el-menu-item>
        <el-menu-item index="/asset/inventory">资产盘点</el-menu-item>
      </el-sub-menu>

      <!-- 报表中心 -->
      <el-sub-menu index="report">
        <template #title>
          <el-icon><DataAnalysis /></el-icon>
          <span>报表中心</span>
        </template>
        <el-menu-item index="/report/subject-balance">科目余额表</el-menu-item>
        <el-menu-item index="/report/balance-sheet">资产负债表</el-menu-item>
        <el-menu-item index="/report/income-statement">利润表</el-menu-item>
        <el-menu-item index="/report/cash-flow">现金流量表</el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app.store'
import {
  HomeFilled, Notebook, Coin, Document, Ticket, Box, DataAnalysis, Setting,
} from '@element-plus/icons-vue'

const route = useRoute()
const appStore = useAppStore()
</script>

<style scoped lang="scss">
.sidebar {
  width: 200px;
  height: 100vh;
  background: #001529;
  transition: width 0.2s;
  overflow: hidden;

  &.collapsed {
    width: 64px;
  }

  .sidebar-logo {
    height: 48px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    background: rgba(0, 0, 0, 0.2);

    .logo-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      line-height: 1.2;

      .logo-brand {
        font-size: 14px;
        font-weight: 600;
      }
    }

    .logo-icon {
      font-size: 18px;
      font-weight: 600;
    }
  }

  .sidebar-menu {
    border-right: none;
    height: calc(100vh - 48px);
    overflow-y: auto;
  }
}
</style>