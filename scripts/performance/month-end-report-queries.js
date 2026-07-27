/**
 * 慧财财务性能测试基准脚本 - 月末结账场景
 * 
 * 场景: 报表聚合查询（科目余额表/资产负债表）模拟高并发读取
 * 工具: k6
 */

import http from 'k6/http';
import { check, sleep, trend, Rate } from 'k6';
import { exec } from 'k6/x/os';

// ------------------- 配置参数 -------------------
const BASE_URL = __ENV.BASE_URL || 'http://localhost:3001';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || 'fake-jwt-token';

// ------------------- 趋势指标 -------------------
const t_subjectBalanceDuration = new Trend('subject_balance_duration_ms');
const t_balanceSheetDuration = new Trend('balance_sheet_duration_ms');
const t_rpsSubjectBalance = new Rate('subject_balance_success_rate');
const t_rpsBalanceSheet = new Rate('balance_sheet_success_rate');

// ------------------- 获取科目余额表 (高频读取场景) -------------------
function getSubjectBalance() {
  const token = AUTH_TOKEN;
  const period = __ENV.PERIOD || '202607'; // 可通过环境变量指定会计期间

  const res = http.get(`${BASE_URL}/report/subject-balance?period=${period}`, {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Accept': 'application/json'
    }
  });

  const durationMs = res.timings.duration;
  t_subjectBalanceDuration.add(durationMs);

  t_rpsRate.add(1);
  
  check(res, {
    'subject balance 200': (r) => r.status === 200,
    'subject balance has data': (r) => r.json().subjects && r.json().subjects.length > 0
  });

  sleep(0.5);
  return res;
}

// ------------------- 获取资产负债表 (较复杂聚合) -------------------
function getBalanceSheet() {
  const token = AUTH_TOKEN;
  const period = __ENV.PERIOD || '202607';

  const res = http.get(`${BASE_URL}/report/balance-sheet?period=${period}`, {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Accept': 'application/json'
    }
  });

  const durationMs = res.timings.duration;
  t_balanceSheetDuration.add(durationMs);

  t_rpsBalanceSheet.add(1);

  check(res, {
    'balance sheet 200': (r) => r.status === 200,
    'balance sheet has assets': (r) => r.json().assets && r.json().assets.totalAmount
  });

  sleep(0.5);
  return res;
}

// ------------------- 主函数 -------------------
export function options() {
  return {
    scenarios: {
      subject_balance_load: {
        executor: 'constant-vus',
        config: {
          vus: 5,          // 5个虚拟用户
          duration: '30s', // 持续30秒
        },
      },
      balance_sheet_load: {
        executor: 'constant-vus',
        config: {
          vus: 3,
          duration: '30s',
        },
      },
    },
  };
}

export default function () {
  // 在场景配置中分别执行，这里保持简单逻辑
  getSubjectBalance();
  getBalanceSheet();
}

/**
 * 预期基准结果示例 (VU=5, 30s):
 *
 *   ✓ subject_balance_success_rate.. 100.00% ✓
 *   ✓ balance_sheet_success_rate.... 100.00% ✓
 *   ✓ subject_balance_duration_ms... avg=342.1 min=289.1 med=321.5 max=412.3 p(95)=398.7 p(99)=410.2
 *   ✓ balance_sheet_duration_ms..... avg=856.4 min=721.3 med=812.6 max=987.5 p(95)=952.1 p(99)=978.3
 */
