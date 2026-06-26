#!/usr/bin/env python3
"""
接口覆盖检测脚本 — 检测前端 API 调用与后端端点的匹配关系.

用法:
  python scripts/check_api_coverage.py

输出:
  - 匹配成功数
  - 后端有前端无 (后端端点未被前端调用)
  - 前端有后端无 (前端调用找不到对应后端端点)

CI 集成: 返回非零退出码当存在不匹配时.
"""

import re
import sys
import json
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
BACKEND_DIR = PROJECT_ROOT / "backend"
FRONTEND_DIR = PROJECT_ROOT / "frontend"


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

        for line in lines:
            # 类级 @RequestMapping
            m = re.search(r'@RequestMapping\s*\(\s*["\']([^"\']+)["\']', line)
            if m and "Controller" in fpath.name:
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
    """标准化路径: 去除 /api/v1 前缀, 替换 ${xxx} 为 {xxx}."""
    p = path.strip("/")
    if p.startswith("api/v1/"):
        p = p[7:]
    p = re.sub(r"\$\{[^}]+\}", lambda m: "{" + m.group(0)[2:-1] + "}", p)
    return "/" + p


def check_coverage():
    """执行接口覆盖检测."""
    backend_endpoints = extract_backend_endpoints()
    frontend_calls = extract_frontend_api_calls()

    backend_norm = {normalize(ep): ep for ep in backend_endpoints}
    frontend_norm = {normalize(fc): fc for fc in frontend_calls}

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

    # CI 退出码: 存在不匹配时返回非零
    if backend_not_covered or frontend_orphan:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(check_coverage())
