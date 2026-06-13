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
      <el-menu-item index="/dashboard">
        <el-icon><HomeFilled /></el-icon>
        <template #title>首页</template>
      </el-menu-item>

      <!-- 系统管理 -->
      <el-sub-menu index="system">
        <template #title>
          <el-icon><Setting /></el-icon>
          <span>系统管理</span>
        </template>
        <el-menu-item index="/system/user" v-if="authStore.hasPermission('system:user:list')">
          <el-icon><User /></el-icon>
          <template #title>用户管理</template>
        </el-menu-item>
        <el-menu-item index="/system/role" v-if="authStore.hasPermission('system:role:list')">
          <el-icon><Avatar /></el-icon>
          <template #title>角色管理</template>
        </el-menu-item>
        <el-menu-item index="/system/menu" v-if="authStore.hasPermission('system:menu:list')">
          <el-icon><Menu /></el-icon>
          <template #title>菜单管理</template>
        </el-menu-item>
        <el-menu-item index="/system/dept" v-if="authStore.hasPermission('system:dept:list')">
          <el-icon><OfficeBuilding /></el-icon>
          <template #title>部门管理</template>
        </el-menu-item>
        <el-menu-item index="/system/audit-log" v-if="authStore.hasPermission('system:audit:list')">
          <el-icon><List /></el-icon>
          <template #title>操作日志</template>
        </el-menu-item>
        <el-menu-item index="/system/clear-data">
          <el-icon><Delete /></el-icon>
          <template #title>数据维护</template>
        </el-menu-item>
      </el-sub-menu>

      <!-- 基础数据 -->
      <el-sub-menu index="basis">
        <template #title>
          <el-icon><Files /></el-icon>
          <span>基础数据</span>
        </template>
        <el-menu-item index="/basis/subject">科目管理</el-menu-item>
        <el-menu-item index="/basis/period">会计期间</el-menu-item>
        <el-menu-item index="/basis/voucher-type">凭证类型</el-menu-item>
        <el-menu-item index="/basis/summary-lib">常用摘要</el-menu-item>
        <el-menu-item index="/basis/config">系统参数</el-menu-item>
      </el-sub-menu>

      <!-- 财务核心 -->
      <el-sub-menu index="finance">
        <template #title>
          <el-icon><Money /></el-icon>
          <span>财务核心</span>
        </template>
        <el-menu-item index="/finance/voucher">凭证管理</el-menu-item>
        <el-menu-item index="/finance/voucher-template">凭证模板</el-menu-item>
        <el-menu-item index="/finance/ledger">账簿查询</el-menu-item>
        <el-menu-item index="/finance/period-close">期末结账</el-menu-item>
        <el-menu-item index="/finance/business-doc">业务单据</el-menu-item>
        <el-menu-item index="/finance/bank-account">银行账户</el-menu-item>
        <el-menu-item index="/finance/bank-journal">银行日记账</el-menu-item>
        <el-menu-item index="/finance/bank-statement">银行对账单</el-menu-item>
        <el-menu-item index="/finance/bank-reconciliation">银行对账</el-menu-item>
        <el-menu-item index="/finance/cash-journal">现金日记账</el-menu-item>
        <el-menu-item index="/finance/ticket">票据管理</el-menu-item>
        <el-menu-item index="/finance/beginning-balance">期初建账</el-menu-item>
        <el-menu-item index="/finance/carryover-guide">结转向导</el-menu-item>
      </el-sub-menu>

      <!-- 固定资产 -->
      <el-sub-menu index="asset">
        <template #title>
          <el-icon><Box /></el-icon>
          <span>固定资产</span>
        </template>
        <el-menu-item index="/asset/category">资产类别</el-menu-item>
        <el-menu-item index="/asset/card">资产卡片</el-menu-item>
        <el-menu-item index="/asset/disposal">资产处置</el-menu-item>
      </el-sub-menu>

      <!-- 往来管理 -->
      <el-sub-menu index="arap">
        <template #title>
          <el-icon><Connection /></el-icon>
          <span>往来管理</span>
        </template>
        <el-menu-item index="/arap/customer">客户档案</el-menu-item>
        <el-menu-item index="/arap/vendor">供应商档案</el-menu-item>
        <el-menu-item index="/arap/receivable">应收明细</el-menu-item>
        <el-menu-item index="/arap/payable">应付明细</el-menu-item>
        <el-menu-item index="/arap/bad-debt">坏账准备</el-menu-item>
        <el-menu-item index="/arap/settlement">往来核销</el-menu-item>
      </el-sub-menu>

      <!-- 税务管理 -->
      <el-sub-menu index="tax">
        <template #title>
          <el-icon><Document /></el-icon>
          <span>税务管理</span>
        </template>
        <el-menu-item index="/tax/input-invoice">进项发票</el-menu-item>
        <el-menu-item index="/tax/output-invoice">销项发票</el-menu-item>
        <el-menu-item index="/tax/vat">增值税计算</el-menu-item>
      </el-sub-menu>

      <!-- 预算管理 -->
      <el-menu-item index="/budget" v-if="authStore.hasPermission('budget:list')">
        <el-icon><PieChart /></el-icon>
        <template #title>预算管理</template>
      </el-menu-item>

      <!-- 报表中心 -->
      <el-sub-menu index="report">
        <template #title>
          <el-icon><DataLine /></el-icon>
          <span>报表中心</span>
        </template>
        <el-menu-item index="/report/subject-balance">科目余额表</el-menu-item>
        <el-menu-item index="/report/balance-sheet">资产负债表</el-menu-item>
        <el-menu-item index="/report/income-statement">利润表</el-menu-item>
        <el-menu-item index="/report/cash-flow">现金流量表</el-menu-item>
      </el-sub-menu>

      <!-- 财务分析 -->
      <el-sub-menu index="analysis">
        <template #title>
          <el-icon><TrendCharts /></el-icon>
          <span>财务分析</span>
        </template>
        <el-menu-item index="/analysis/key-metrics">关键指标</el-menu-item>
        <el-menu-item index="/analysis/dupont">杜邦分析</el-menu-item>
      </el-sub-menu>

      <!-- AI 中心 -->
      <el-sub-menu index="ai">
        <template #title>
          <el-icon><MagicStick /></el-icon>
          <span>AI 中心</span>
        </template>
        <el-menu-item index="/ai/task">AI 任务</el-menu-item>
        <el-menu-item index="/ai/anomaly">AI 异常</el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app.store'
import { useAuthStore } from '@/stores/auth.store'
import {
  HomeFilled, Setting, User, Avatar, Menu, OfficeBuilding, List, Files, Money, Box,
  Connection, Document, PieChart, DataLine, TrendCharts, MagicStick, Delete,
} from '@element-plus/icons-vue'

const route = useRoute()
const appStore = useAppStore()
const authStore = useAuthStore()
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