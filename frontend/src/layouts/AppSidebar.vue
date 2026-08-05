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
        <el-menu-item index="/finance/voucher-template-ref">模板参考库</el-menu-item>
        <el-menu-item index="/finance/bank-account">银行账户</el-menu-item>
      </el-sub-menu>

      <!-- 财务核心 -->
      <el-sub-menu index="finance">
        <template #title>
          <el-icon><Coin /></el-icon>
          <span>财务核心</span>
        </template>
        <el-menu-item index="/finance/voucher">凭证管理</el-menu-item>
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
        <!-- 票据管理暂时隐藏（路由仍保留，恢复时取消注释） -->
        <!-- <el-menu-item index="/finance/ticket">票据管理</el-menu-item> -->
        <el-menu-item index="/arap/reconciliation">核销管理</el-menu-item>
        <el-menu-item index="/arap/expense">费用报销</el-menu-item>
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

      <!-- 代理公司（SUPER_ADMIN / AGENCY 可见） -->
      <el-sub-menu v-if="authStore.isSuperAdmin || authStore.isAgency" index="agency">
        <template #title>
          <el-icon><OfficeBuilding /></el-icon>
          <span>代理公司</span>
        </template>
        <el-menu-item index="/agency/enterprise-list">客户列表</el-menu-item>
        <el-menu-item index="/agency/batch-operation">批量操作</el-menu-item>
        <el-menu-item v-if="authStore.isAgencyAdmin" index="/agency/accountant-list">会计管理</el-menu-item>
        <el-menu-item v-if="authStore.isAgencyAdmin" index="/agency/assignment-manage">客户分配</el-menu-item>
        <el-menu-item v-if="authStore.isAgencyAdmin" index="/agency/dashboard">主管仪表盘</el-menu-item>
      </el-sub-menu>

      <!-- 系统管理（所有用户可见） -->
      <el-sub-menu index="system-mgmt">
        <template #title>
          <el-icon><Setting /></el-icon>
          <span>系统管理</span>
        </template>
        <el-menu-item index="/system/user">用户管理</el-menu-item>
        <el-menu-item index="/system/role">角色管理</el-menu-item>
        <el-menu-item index="/system/menu">菜单管理</el-menu-item>
        <el-menu-item index="/system/dept">部门管理</el-menu-item>
        <el-menu-item index="/system/audit-log">操作日志</el-menu-item>
      </el-sub-menu>

      <!-- 实验室（SUPER_ADMIN 且 Flag 开启时可见） -->
      <el-sub-menu v-if="authStore.isSuperAdmin && labStore.enabled" index="lab">
        <template #title>
          <el-icon><MagicStick /></el-icon>
          <span>实验室</span>
        </template>
        <el-menu-item index="/budget">预算管理</el-menu-item>
        <el-menu-item index="/analysis/key-metrics">关键指标</el-menu-item>
        <el-menu-item index="/analysis/dupont">杜邦分析</el-menu-item>
        <el-menu-item index="/salary">工资薪酬</el-menu-item>
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
import { useLabStore } from '@/stores/lab.store'
import {
  HomeFilled, Notebook, Coin, Document, Ticket, Box, DataAnalysis, Setting,
  User, OfficeBuilding, Monitor, TrendCharts, MagicStick,
} from '@element-plus/icons-vue'

const route = useRoute()
const appStore = useAppStore()
const authStore = useAuthStore()
const labStore = useLabStore()
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