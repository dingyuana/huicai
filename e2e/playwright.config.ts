import { defineConfig, devices } from '@playwright/test';

/**
 * 慧财财务 E2E 冒烟测试配置
 * 目标环境：已部署的 huicai 实例
 * 登录凭证：admin / admin123
 */
export default defineConfig({
  // 测试目录
  testDir: './tests',
  // 每个测试独立上下文
  fullyParallel: true,
  // 失败时重试 1 次
  retries: process.env.CI === '1' ? 2 : 1,
  // 最大并发 worker 数
  workers: process.env.CI === '1' ? 2 : 1,
  // 报告器
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list'],
  ],

  // 全局超时
  timeout: 30_000,

  // 全局浏览器配置
  use: {
    // 目标地址（可通过环境变量覆盖）
    baseURL: process.env.E2E_BASE_URL ?? 'http://129.211.7.254:3001',

    // 截图策略：仅在失败时截图
    screenshot: 'only-on-failure',
    // 视频策略：不录制（避免依赖 ffmpeg）
    video: 'off',
    // 追踪：仅在失败时保存
    trace: 'retain-on-failure',
    // 上下文超时
    actionTimeout: 10_000,
    navigationTimeout: 15_000,

    // 浏览器大小
    viewport: { width: 1440, height: 900 },

    // 忽略 HTTPS 证书错误
    acceptDownloads: true,
  },

  // 项目定义
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // 使用系统已安装的 Chrome，避免下载 177MB 的 Playwright Chromium
        channel: 'chrome',
      },
    },
  ],
});
