#!/usr/bin/env python3
"""
GitHub Actions workflow 结构守卫。

🔴 为什么需要它：workflow 的错误**不会让构建失败**，而是等到部署那一刻才炸；
而部署失败往往表现为「红了一大片但看不出原因」。本项目已实际踩过两类：

  ① **env 挂错层级** —— GitHub 的 ``env`` 是 **step 级**作用域。写在 job 级或
     别的 step 上，在本 step 内取不到；而 ``ssh-action`` 的脚本带 ``set -euo pipefail``，
     取不到就是「unbound variable」直接失败。
  ② **同 step 内重复 env** —— YAML 里同名 key 出现两次时，后者**静默覆盖**前者，
     前者连同它携带的 ``${{ secrets.* }}`` 一起消失。肉眼几乎看不出问题。

这两类都无法靠「读一遍 YAML」发现，必须机械校验。
"""
import os
import re
import sys

import yaml

WORKFLOW_DIR = '/home/dy/huicai/.github/workflows'

# GitHub 内置环境变量，不需要在 step 里声明
BUILTIN = {
    'GITHUB_SHA', 'GITHUB_REF', 'GITHUB_BRANCH', 'GITHUB_REPOSITORY',
    'GITHUB_RUN_ID', 'GITHUB_WORKSPACE', 'GITHUB_ACTOR', 'GITHUB_ENV',
    'RUNNER_OS', 'RUNNER_ARCH', 'CI', 'HOME', 'PATH', 'PWD', 'JAVA_HOME',
}

VAR_RE = re.compile(r'\$\{?([A-Z_][A-Z0-9_]*)\}?')


def check_workflow(path):
    """返回该 workflow 的问题列表。"""
    problems = []
    with open(path, encoding='utf-8') as f:
        raw = f.read()

    # ① YAML 重复 key 检查：PyYAML 默认静默取最后一个
    dups = find_duplicate_keys(raw)
    for key, spots in dups.items():
        first, again = spots[0]
        problems.append(f'YAML 重复 key `{key}`（第 {first} 行与第 {again} 行等 {len(spots)+1} 处）'
                        f' —— 后者会静默覆盖前者；若两次都是 env，前者携带的 secrets 会一并消失')

    doc = yaml.safe_load(raw)
    if not doc or 'jobs' not in doc:
        return problems

    for job_name, job in doc['jobs'].items():
        steps = job.get('steps') or []
        for idx, step in enumerate(steps):
            name = step.get('name', '<unnamed>')
            where = f'job[{job_name}].step[{idx}]({name})'

            # ② env 必须是 step 的直接子键，不能出现在 with/uses 里
            with_block = step.get('with') or {}
            if isinstance(with_block, dict) and 'env' in with_block:
                problems.append(f'{where}: `env` 出现在 `with` 内 —— GitHub 不认，'
                                f'该变量在脚本里取不到（会在 set -u 下直接失败）')

            env = step.get('env') or {}
            declared = set(env.keys()) | BUILTIN

            body = ''
            if isinstance(with_block, dict):
                body += str(with_block.get('script') or '')
                body += str(with_block.get('entrypoint') or '')
            body += str(step.get('run') or '')

            if '$' not in body:
                continue

            # 脚本内部自己赋值的变量不算缺失
            local_vars = set(re.findall(r'^\s*([A-Z_][A-Z0-9_]*)=', body, re.M))
            local_vars |= set(re.findall(r'for\s+([A-Z_][A-Z0-9_]*)\s+in', body))
            local_vars |= set(re.findall(r'^\s*(?:export\s+)?([A-Z_][A-Z0-9_]*)=\$\(', body, re.M))

            used = {v for v in VAR_RE.findall(body)
                    if v not in declared and v not in local_vars}
            # shell 内建命令
            used -= {'SECONDS', 'RANDOM', 'LINENO', 'PWD', 'IFS', 'PATH'}
            if used:
                problems.append(f'{where}: 脚本使用了未声明的环境变量 {sorted(used)}'
                                f'（env 是 step 级作用域，别处声明取不到）')
    return problems


class DupKeyLoader(yaml.SafeLoader):
    """PyYAML 默认对重复 key **静默取最后一个**。

    本项目真实故障：`Deploy via SSH` step 里写了两次 `env`，后者覆盖前者，
    前者携带的 `${{ secrets.* }}` 一并消失 ⇒ 变量在脚本里变成 unbound，
    而 yaml.safe_load 完全看不出异常。
    """


def _no_dup(loader, node, deep=False):
    mapping = {}
    for key_node, value_node in node.value:
        # 🔴 key 名直接取**标量原始值**：走 construct_object 会因惰性构造
        #    拿到已填充的 mapping（表现为报错里 key 显示成 "{'A': 'x'}" 这种字典）。
        kn = key_node
        key = getattr(kn, 'value', None)
        if not isinstance(key, (str, int, float, bool)):
            key = loader.construct_object(kn, deep=True)
            if not isinstance(key, (str, int, float, bool)):
                key = str(key)
        if key in mapping:
            mark = (kn.start_mark.line + 1)
            DUPS.append((str(key), mapping[key], mark))
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping


DupKeyLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _no_dup)


def find_duplicate_keys(raw):
    """🔴 检测**同一个映射节点内**的重复 key。

    🔴 判据不能用「缩进相同」：不同 step 各有一个 env 是**完全正常**的
    （GitHub 的 env 本就是 step 级作用域），按缩进粗比会把它们全判成重复
    ⇒ 守卫恒红 ⇒ 再也没人看它（AGENTS §4.5 第 21 条「恒红即无信号」）。
    故改为在 <b>解析期</b>捕获：同一个 mapping 节点里出现两次同名 key 才算。
    """
    DUPS.clear()
    try:
        yaml.load(raw, Loader=DupKeyLoader)
    except yaml.YAMLError:
        # 解析失败会由 yaml.safe_load 在调用方单独报出
        return {}
    dups = {}
    for key, first, again in DUPS:
        dups.setdefault(key, []).append((first, again))
    return dups


DUPS = []


def main():
    if not os.path.isdir(WORKFLOW_DIR):
        print(f'工作流目录不存在: {WORKFLOW_DIR}')
        return 1

    files = sorted(f for f in os.listdir(WORKFLOW_DIR) if f.endswith(('.yml', '.yaml')))
    all_problems = []
    for fn in files:
        probs = check_workflow(os.path.join(WORKFLOW_DIR, fn))
        if probs:
            print(f'\n[{fn}]')
            for p in probs:
                print(f'  - {p}')
        all_problems += [(fn, p) for p in probs]

    print(f'\n已检查 workflow: {len(files)} 个')
    if all_problems:
        print(f'❌ 发现 {len(all_problems)} 个结构问题')
        return 1
    print('✅ workflow 结构校验通过（env 作用域 + 重复 key + 未声明变量）')
    return 0


if __name__ == '__main__':
    sys.exit(main())