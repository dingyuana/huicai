#!/usr/bin/env python3
"""
接口覆盖检测脚本 — 检测前端 API 调用与后端端点的匹配关系.

用法:
  python scripts/check_api_coverage.py                     # 棘轮模式（CI 用，会阻断）
  python scripts/check_api_coverage.py --report-only       # 只报告恒绿（摸底用）
  python scripts/check_api_coverage.py --max-uncovered 200 # 显式指定上限

输出:
  - 匹配成功数
  - 后端有前端无 (后端端点未被前端调用)
  - 前端有后端无 (前端调用找不到对应后端端点)

关于退出码（2026-10-03 改为棘轮，勿退回「有差异就红」）:
  改造前只要存在任一不匹配就 exit 1，而当时基线是**后端 280 个端点前端未接**。
  该门禁因此被 workflow 用 `continue-on-error: true` 挂起 —— 也就是**恒绿**，
  恒绿与恒红同样有害（AGENTS §4.5 第 21 条）：真出问题时也没人看。
  但直接去掉 continue-on-error 会变成**恒红**，280 条存量会让所有人习惯性忽略门禁。
  故改为与 JaCoCo 覆盖率同款的**棘轮**：以当前实测值为上限，只在**倒退**时红。
  ⚠️ 每修掉一批端点接入，必须回来下调 `--max-uncovered`，否则棘轮退化成固定上限
  （同 AGENTS §4.5 第 25 条）。
"""

import argparse
import re
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
BACKEND_DIR = PROJECT_ROOT / "backend"
FRONTEND_DIR = PROJECT_ROOT / "frontend"

# 棘轮基线：2026-10-03 实测（后端 425 端点 / 前端 148 调用 / 匹配 145 / 未接 280 / 孤儿 0）
DEFAULT_MAX_UNCOVERED = 280
DEFAULT_MAX_ORPHAN = 0


def extract_backend_endpoints():
    """提取后端所有 Controller 端点."""
    controllers = {}
    current_ctrl = ""
    endpoints = []

    # 找到所有 Controller 文件
    controller_files = sorted(BACKEND_DIR.glob("src/main/java/**/*Controller.java"))

    for fpath in controller_files:
        content = fpath.read_text(encoding="utf-8")
        lines = content.split("\n")
        current_ctrl = ""
        for line in lines:
            # 类级 @RequestMapping
            m = re.search(r'@RequestMapping\s*\(\s*["\']([^"\']+)["\']', line)
            if m:
                current_ctrl = m.group(1)

            # 方法级注解
            for method in ["PostMapping", "GetMapping", "PutMapping", "DeleteMapping"]:
                m2 = re.search(r'@' + method + r'\s*\(\s*["\']([^"\']*)["\']', line)
                if m2:
                    sub = m2.group(1)
                    full = current_ctrl + ("/" if not sub.startswith("/") else "") + sub
                    endpoints.append(full)

    return sorted(set(endpoints))


def extract_frontend_api_calls():
    """提取前端所有 API 调用."""
    calls = []

    # 找到所有 API 模块文件
    api_files = sorted(FRONTEND_DIR.glob("src/api/**/*.ts"))

    for fpath in api_files:
        content = fpath.read_text(encoding="utf-8")
        # 匹配 request.get/post/put/delete 的 URL
        # 模板字符串: request.post(`/tax/output-invoices/${id}/confirm`)
        for m in re.finditer(r"request\.(get|post|put|delete)\s*\(\s*`([^`]+)`", content):
            calls.append(m.group(2))
        # 普通字符串: request.get('/tax/types/page')
        for m in re.finditer(r"request\.(get|post|put|delete)\s*\(\s*'([^']+)'", content):
            calls.append(m.group(1))

    return sorted(set(calls))


def normalize(path):
    """标准化路径: 去除 /api 前缀, 替换 ${xxx} 为 {xxx}, 去除查询参数, 过滤噪声."""
    # 去除查询参数 (trace?no=xxx → trace)
    path = path.split("?")[0]
    p = path.strip("/")
    # 后端路径有 /api 前缀, 前端路径没有
    if p.startswith("api/"):
        p = p[4:]
    # 统一路径参数: 无论 ${xxx} 还是 {xxx}, 全部替换为 {}
    # 避免因参数名不同 (e.g. {id} vs {logId}) 导致匹配失败
    p = re.sub(r"\$\{[^}]+\}", "{}", p)
    p = re.sub(r"\{[^}]+\}", "{}", p)
    # 过滤噪声 (空路径, 纯 HTTP 方法等)
    if not p or p in ("get", "post", "put", "delete"):
        return None
    return "/" + p


def check_coverage(max_uncovered: int = DEFAULT_MAX_UNCOVERED,
                   max_orphan: int = DEFAULT_MAX_ORPHAN,
                   report_only: bool = False) -> int:
    """执行接口覆盖检测。"""
    backend_endpoints = extract_backend_endpoints()
    frontend_calls = extract_frontend_api_calls()

    backend_norm = {}
    for ep in backend_endpoints:
        n = normalize(ep)
        if n is not None:
            backend_norm[n] = ep
    frontend_norm = {}
    for fc in frontend_calls:
        n = normalize(fc)
        if n is not None:
            frontend_norm[n] = fc

    matched = []
    backend_not_covered = []
    frontend_orphan = []

    for bn, bp in sorted(backend_norm.items()):
        if bn in frontend_norm:
            matched.append((bn, bp, frontend_norm[bn]))
        else:
            backend_not_covered.append((bn, bp))

    for fn, fp in sorted(frontend_norm.items()):
        if fn not in backend_norm:
            frontend_orphan.append((fn, fp))

    # 输出报告
    print("=" * 60)
    print("接口覆盖检测报告")
    print("=" * 60)
    print(f"后端端点总数: {len(backend_endpoints)}")
    print(f"前端 API 调用总数: {len(frontend_calls)}")
    print(f"✅ 匹配成功: {len(matched)}")
    print(f"❌ 后端有前端无: {len(backend_not_covered)}")
    print(f"⚠️ 前端有后端无: {len(frontend_orphan)}")

    if backend_not_covered:
        print(f"\n--- 后端有前端无 (后端端点未被前端调用) ---")
        for bn, bp in backend_not_covered:
            print(f"  ❌ {bp}")

    if frontend_orphan:
        print(f"\n--- 前端有后端无 (前端调用找不到对应后端端点) ---")
        for fn, fp in frontend_orphan:
            print(f"  ⚠️ {fp}")

    # 棘轮判定：只在**倒退**（超过上限）时红。存量未接端点是清理项，不是门禁失败。
    n_uncovered = len(backend_not_covered)
    n_orphan = len(frontend_orphan)
    over_uncovered = n_uncovered - max_uncovered
    over_orphan = n_orphan - max_orphan
    print(f"\n棘轮上限: 后端未接 ≤ {max_uncovered}（当前 {n_uncovered}，余量 {max_uncovered - n_uncovered}）"
          f" / 前端孤儿 ≤ {max_orphan}（当前 {n_orphan}，余量 {max_orphan - n_orphan}）")

    if report_only:
        print("[报告模式] 不阻断（仅摸底用，CI 不应使用该模式）")
        return 0
    if over_uncovered > 0 or over_orphan > 0:
        print(f"\n[FAIL] 接口覆盖较棘轮基线倒退："
              f"后端未接 +{over_uncovered}，前端孤儿 +{over_orphan}")
        if n_orphan > max_orphan:
            print("  前端孤儿 = 前端调了不存在的后端端点，属真断裂，必须立即修")
        print("  若是新增端点未接前端，请把它接入前端或登记为「内部/暂不暴露」，"
              "不要靠放宽上限过关")
        return 1
    print("[OK] 接口覆盖未倒退")
    return 0


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="接口覆盖检测（棘轮模式）")
    ap.add_argument("--max-uncovered", type=int, default=DEFAULT_MAX_UNCOVERED,
                    help=f"后端未接端点上限（默认 {DEFAULT_MAX_UNCOVERED}）")
    ap.add_argument("--max-orphan", type=int, default=DEFAULT_MAX_ORPHAN,
                    help=f"前端孤儿调用上限（默认 {DEFAULT_MAX_ORPHAN}）")
    ap.add_argument("--report-only", action="store_true",
                    help="只报告不阻断（摸底用，勿在 CI 使用）")
    args = ap.parse_args()
    sys.exit(check_coverage(args.max_uncovered, args.max_orphan, args.report_only))
