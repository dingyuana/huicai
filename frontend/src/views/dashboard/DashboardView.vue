<template>
  <div class="dashboard">
    <h2>欢迎使用慧财智能财务平台</h2>
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover">
          <p class="stat-label">本月流水</p>
          <p class="stat-value">-- 笔</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <p class="stat-label">待审核凭证</p>
          <p class="stat-value warning">-- 张</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <p class="stat-label">本月净利润</p>
          <p class="stat-value positive">¥--</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <p class="stat-label">待处理流水</p>
          <p class="stat-value danger">-- 条</p>
        </el-card>
      </el-col>
    </el-row>
    <el-card class="quick-actions">
      <template #header><span>快速入口</span></template>
      <el-space wrap>
        <el-button type="primary">导入银行流水</el-button>
        <el-button>新增凭证</el-button>
        <el-button>结账体检</el-button>
      </el-space>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.dashboard {
  padding: 24px;
  h2 { margin-bottom: 24px; }
  .stat-cards {
    margin-bottom: 24px;
  }
  .stat-label {
    font-size: 13px;
    color: #999;
    margin-bottom: 8px;
  }
  .stat-value {
    font-size: 24px;
    font-weight: 600;
    color: #333;
    &.warning { color: #faad14; }
    &.positive { color: #cf1322; }
    &.danger { color: #ff4d4f; }
  }
}
</style>