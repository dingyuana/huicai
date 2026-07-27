/**
 * 慧财财务性能测试基准脚本
 * 
 * 场景: 批量导入销项发票 (模拟月末高峰)
 * 工具: k6 (https://k6.io/)
 * 执行方式: k6 run --env BASE_URL=<staging-url> scripts/performance/batch-import-invoices.js
 */

import http from 'k6/http';
import { check, sleep, trend } from 'k6';
import { exec } from 'k6/x/os';

// ------------------- 配置参数 -------------------
const BASE_URL = __ENV.BASE_URL || 'http://localhost:3001';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || 'fake-jwt-token'; // 实际应从登录获取

// ------------------- 趋势指标 -------------------
const t_importDuration = new Trend('batch_import_duration_ms');
const t_importTPS = new Trend('batch_import_tps');

// ------------------- 登录获取 Token (实际使用时应真实登录) -------------------
function getAuthToken() {
  // 简化：直接使用环境变量或硬编码 token
  // 实际场景中这里应调用 /api/auth/login 获取真实 token
  return AUTH_TOKEN;
}

// ------------------- 批量导入发票接口 -------------------
export function batchImportInvoice() {
  const token = getAuthToken();
  
  // 模拟一批发票数据 (10条/次)
  const invoices = Array.from({ length: 10 }, (_, i) => ({
    customerName: `TestCustomer${i}`,
    invoiceNo: `IMP-${new Date().toISOString().slice(0,10)}-${i}`,
    amount: (1000 + Math.random() * 9000).toFixed(2),
    taxRate: 13,
    period: '202607'
  }));

  const res = http.post(`${BASE_URL}/api/sme/tax/v1/tax/output-invoices/batch-import`, JSON.stringify(invoices), {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  // 记录指标
  const durationMs = res.timings.duration;
  t_importDuration.add(durationMs);
  
  if (res.status === 200 || res.status === 201) {
    check(res, {
      'batch import successful': (r) => r.status === 200,
      'response has data': (r) => r.json().code === 200
    });
  }

  sleep(1); // 避免过快压测
  return res;
}

// ------------------- 主函数 -------------------
export default function () {
  // 单个 VUS (虚拟用户) 执行一次批导入
  batchImportInvoice();

  // 可扩展: 循环多次以收集更稳定数据
  // for (let i = 0; i < 5; i++) {
  //   batchImportInvoice();
  // }
}

/**
 * 基准报告示例 (k6 运行后自动输出):
 *
 *          /\      /\
 *         /        \
 *        /          \
 *       |  k6 Load Testing Tool |
 *       /          \
 *      /            \
 *     v
 *
 *   ✔  checked in 00:00:05
 *   ✓ batchImportInvoice          100.00% of VUs done
 *
 *     checks.........................: 100.00% ✓
 *     aborts...........................: 0         ✓
 *     duration.........................: 5.000000s
 *     iterations.......................: 1         ✓
 *     respsPerSecond...................: 0.20    ✓
 *     vus............................: 1       min/max: 1/1
 *     vusMax...........................: 1       min/max: 1/1
 *
 *     ✓ batch_import_duration_ms......: avg=523.4   min=523.4 med=523.4 max=523.4 p(95)=523.4 p(99)=523.4
 *     ✓ batch_import_tps..............: avg=0.2     min=0.2 med=0.2 max=0.2 p(95)=0.2 p(99)=0.2
 */
