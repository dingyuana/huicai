#!/usr/bin/env python3
"""
API契约校验脚本：遍历前端API定义，验证后端是否存在对应Controller映射

使用方式：
    python scripts/check-api-contract.py
    python scripts/check-api-contract.py --backend http://localhost:8000
"""

import os
import re
import sys
import json
import argparse
import subprocess
from urllib.parse import urljoin

try:
    import requests
except ImportError:
    print("请先安装 requests: pip install requests")
    sys.exit(1)


def extract_api_paths(api_dir):
    """从前端API模块文件中提取所有HTTP请求路径和方法"""
    api_paths = []
    
    for filename in os.listdir(api_dir):
        if not filename.endswith('.ts'):
            continue
        
        filepath = os.path.join(api_dir, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 匹配 request.get('/path')
        get_pattern = r"request\.get\(['\"]([^'\"]+)['\"]"
        post_pattern = r"request\.post\(['\"]([^'\"]+)['\"]"
        put_pattern = r"request\.put\(['\"]([^'\"]+)['\"]"
        delete_pattern = r"request\.delete\(['\"]([^'\"]+)['\"]"
        
        for match in re.finditer(get_pattern, content):
            api_paths.append(('GET', match.group(1)))
        
        for match in re.finditer(post_pattern, content):
            api_paths.append(('POST', match.group(1)))
        
        for match in re.finditer(put_pattern, content):
            api_paths.append(('PUT', match.group(1)))
        
        for match in re.finditer(delete_pattern, content):
            api_paths.append(('DELETE', match.group(1)))
    
    # 去重
    return list(set(api_paths))


def get_backend_endpoints(backend_url):
    """从后端获取所有注册的API端点"""
    endpoints = set()
    
    try:
        # 尝试从Swagger/OpenAPI获取
        swagger_url = urljoin(backend_url, '/api-docs')
        resp = requests.get(swagger_url, timeout=10)
        if resp.status_code == 200:
            data = resp.json()
            for path, methods in data.get('paths', {}).items():
                for method in methods.keys():
                    endpoints.add((method.upper(), path))
        
        # 尝试从actuator获取
        actuator_url = urljoin(backend_url, '/actuator/mappings')
        resp = requests.get(actuator_url, timeout=10)
        if resp.status_code == 200:
            data = resp.json()
            for mapping in data.get('contexts', {}).values():
                for servlet in mapping.get('mappings', {}).get('dispatcherServlets', {}).values():
                    for entry in servlet:
                        if isinstance(entry, dict):
                            methods = entry.get('methods', [])
                            pattern = entry.get('pattern', '')
                            for method in methods:
                                endpoints.add((method, pattern))
    except Exception as e:
        print(f"无法获取后端端点: {e}")
        return endpoints
    
    return endpoints


def check_contract(api_dir, backend_url):
    """执行API契约校验"""
    print(f"提取前端API路径...")
    frontend_paths = extract_api_paths(api_dir)
    print(f"找到 {len(frontend_paths)} 个前端API调用")
    
    print(f"\n获取后端端点...")
    backend_endpoints = get_backend_endpoints(backend_url)
    print(f"找到 {len(backend_endpoints)} 个后端端点")
    
    print(f"\n=== 契约校验结果 ===")
    missing = []
    
    for method, path in sorted(frontend_paths):
        # 完整路径
        full_path = f"/api/v1{path}" if not path.startswith('/api') else path
        
        found = False
        for be_method, be_pattern in backend_endpoints:
            if method == be_method and (be_pattern == full_path or be_pattern.startswith(full_path)):
                found = True
                break
        
        if found:
            print(f"✓ {method} {path}")
        else:
            print(f"✗ {method} {path}")
            missing.append((method, path))
    
    if missing:
        print(f"\n⚠️  发现 {len(missing)} 个缺失的后端接口:")
        for method, path in missing:
            print(f"  - {method} {path}")
        return False
    
    print("\n✅ 所有前端API在后端均有对应实现")
    return True


def main():
    parser = argparse.ArgumentParser(description='API契约校验')
    parser.add_argument('--api-dir', default='frontend/src/api/modules',
                        help='前端API模块目录')
    parser.add_argument('--backend', default='http://localhost:8000',
                        help='后端服务地址')
    args = parser.parse_args()
    
    api_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), args.api_dir)
    
    if not os.path.exists(api_dir):
        print(f"错误: API目录不存在 - {api_dir}")
        sys.exit(1)
    
    success = check_contract(api_dir, args.backend)
    sys.exit(0 if success else 1)


if __name__ == '__main__':
    main()
