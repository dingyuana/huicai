#!/usr/bin/env python3
"""
路由覆盖检测脚本 — 检测前端路由与组件文件的匹配关系.

用法:
  python scripts/check_route_coverage.py

输出:
  - 路由定义数
  - 组件文件存在数
  - 路由定义缺失 (组件存在但路由未定义)
  - 组件文件缺失 (路由定义但组件不存在)

CI 集成: 返回非零退出码当存在不匹配时.
"""

import re
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
FRONTEND_DIR = PROJECT_ROOT / "frontend"


def extract_routes():
    """提取前端路由表中的所有路由定义."""
    route_file = FRONTEND_DIR / "src" / "router" / "routes" / "base.ts"
    if not route_file.exists():
        print(f"❌ 路由文件不存在: {route_file}")
        return []

    content = route_file.read_text(encoding="utf-8")

    routes = []
    # 匹配路由定义: { path: 'xxx', name: 'Yyy', component: () => import('...') }
    # 使用更宽松的正则来捕获
    route_pattern = re.compile(
        r'\{\s*path:\s*[\'"]([^\'"]+)[\'"]\s*,\s*name:\s*[\'"]([^\'"]+)[\'"]\s*,\s*component:\s*\(\)\s*=>\s*import\([\'"]([^\'"]+)[\'"]\)',
        re.MULTILINE
    )

    for m in route_pattern.finditer(content):
        path = m.group(1)
        name = m.group(2)
        component = m.group(3)
        routes.append({
            "path": path,
            "name": name,
            "component": component,
        })

    return routes


def extract_component_files():
    """提取所有组件文件."""
    views_dir = FRONTEND_DIR / "src" / "views"
    if not views_dir.exists():
        return []

    components = []
    for vue_file in sorted(views_dir.glob("**/*.vue")):
        rel_path = vue_file.relative_to(views_dir)
        components.append({
            "path": str(rel_path),
            "name": vue_file.stem,
        })

    return components


def normalize_component_path(component_import):
    """将组件 import 路径转换为相对路径."""
    # 例: '@/views/finance/business-doc/BusinessDocEdit.vue'
    # 转换为: 'finance/business-doc/BusinessDocEdit.vue'
    if component_import.startswith("@/views/"):
        return component_import[8:]  # 去掉 '@/views/'
    return component_import


def check_coverage():
    """执行路由覆盖检测."""
    routes = extract_routes()
    components = extract_component_files()

    # 建立组件名称到文件路径的映射
    component_map = {c["name"]: c["path"] for c in components}

    route_names = {r["name"] for r in routes}
    component_names = {c["name"] for c in components}

    # 检查路由定义是否都有对应组件
    missing_components = []
    for route in routes:
        comp_path = normalize_component_path(route["component"])
        # 检查组件文件是否存在
        comp_file = FRONTEND_DIR / "src" / "views" / comp_path
        if not comp_file.exists():
            missing_components.append({
                "route": route["name"],
                "path": route["path"],
                "expected_component": comp_path,
            })

    # 检查组件是否都有对应路由
    orphan_components = []
    for comp in components:
        if comp["name"] not in route_names:
            orphan_components.append({
                "component": comp["name"],
                "path": comp["path"],
            })

    # 输出报告
    print("=" * 60)
    print("路由覆盖检测报告")
    print("=" * 60)
    print(f"路由定义数: {len(routes)}")
    print(f"组件文件数: {len(components)}")
    print(f"✅ 路由-组件匹配: {len(routes) - len(missing_components)}")
    print(f"❌ 组件文件缺失: {len(missing_components)}")
    print(f"⚠️ 路由定义缺失: {len(orphan_components)}")

    if missing_components:
        print(f"\n--- 组件文件缺失 (路由定义但组件不存在) ---")
        for item in missing_components:
            print(f"  ❌ 路由 {item['route']} (path: {item['path']}) → 组件不存在: {item['expected_component']}")

    if orphan_components:
        print(f"\n--- 路由定义缺失 (组件存在但路由未定义) ---")
        for item in orphan_components:
            print(f"  ⚠️ 组件 {item['component']} ({item['path']}) → 路由未定义")

    # CI 退出码: 存在不匹配时返回非零
    if missing_components or orphan_components:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(check_coverage())
