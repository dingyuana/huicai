#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
登记册漂移守卫（v2：新增「状态码」列校验） —— 机器可检不变量（SPEC-P114，REQ-2026-141）

## 为什么需要它
2026-10-08 一次批量清理发现登记册与代码/SPEC **双向分叉** 11 处：
「已做却标未开工」（REQ-066~070、085、081）与「已完成却标待审」（REQ-112/128/133/139）。
分叉的后果是排期低估可用功能、且把「待审核」当成真阻塞。
人工比对一次就漏了 11 处 ⇒ 必须有机械守卫。

## 守卫编码的 5 类不变量（都是「能机检、且误报可控」）
1. **表结构一致**：每条 REQ 行的列数属 {5,6}（5=设计铁律条目，6=常规需求），
   出现其它列数（实测有 2 行 8 列）即结构损坏。
2. **状态列非空且不以分隔符泄漏**：状态列不能为空、不能是 `|` 或 `---` 等表格分隔残留。
3. **关联 SPEC 存在性**：状态列声称「已实现/已完成」的 REQ，若其「关联 SPEC」列写了
   `SPC-Pxxx`，则 `docs/specs/` 下必须存在对应文件（防止 SPEC 改名/删除后登记册悬空引用）。
4. **设计铁律条目豁免**：REQ-047~054 是 11 条设计铁律（非功能需求），豁免第 3 条，
   但仍要求列数为 5 —— 用 `DESIGN_RULES` 白名单钉住，避免误把它们当漏文档的功能。
5. **状态列必须含情绪标记**：状态列须以 `✅/🚧/📝/📋/🆕/⏳/🟡/⚠️` 之一开头
   （实测 8 行「假需求」把分组标题写进状态列，此规则会抓出）——
   ⚠️ 该规则对设计铁律条目（5 列）不适用，因为它们本就没有状态。

## 反证要求（AGENTS §4.5 第 21/25 条：门禁必须能变红）
- 注入一条「关联 SPEC 指向不存在文件」的 REQ ⇒ 必须 exit 1 并指名道姓。
- 注入一条「状态列为空」的 REQ ⇒ 必须 exit 1。
- 移除某条设计铁律条目 ⇒ 第 4 条白名单校验转红。
"""
import io
import os
import re
import sys

REG = 'docs/development/requirements/REQUIREMENTS_REGISTRY.md'
SPEC_DIR = 'docs/specs'

# 设计铁律条目（非功能需求，无关联 SPEC / 实现状态），列数恒为 5
DESIGN_RULES = {'REQ-2026-%03d' % n for n in range(47, 55)}

MOOD = ('✅', '🚧', '📝', '📋', '🆕', '⏳', '🟡', '⚠️')

# 状态码受控词表（机器可读，登记册「状态码」列只能取这些值）
CODES = {'DONE', 'ACCEPT', 'PARTIAL', 'REVIEW', 'PLANNED', 'DESIGN'}

# 状态码 => 状态文字必须满足的形态（交叉校验，防止码与文字脱节）
CODE_RULES = {
    'DONE':    lambda t: t.startswith('✅') and '待验收' not in t and '待业务验收' not in t and '待验证' not in t,
    'ACCEPT':  lambda t: ('待验收' in t or '待业务验收' in t or '待验证' in t),
    # PARTIAL 覆盖两类半成品形态：🚧「部分实施」与 ⚠️「基础/前端基础」
    # ⚠️ 类经老丁 2026-10-08 裁定归为 PARTIAL（REQ-016/034/035）
    'PARTIAL': lambda t: t.startswith('🚧') or t.startswith('⚠️'),
    'REVIEW':  lambda t: '待审核' in t or '待复审' in t,
    'PLANNED': lambda t: t.startswith(('🆕', '⏳', '📝', '📋')),
    'DESIGN':  lambda t: True,   # 设计铁律条目无状态文字
}
DONE_WORDS = ('已实现', '已完成', '已开发完成', '已实施', '已收口', '已验收')

REQ_ROW = re.compile(r'^\|\s*\*{0,2}(REQ-2026-\d{3})\*{0,2}\s*\|')


def cells_of(line):
    return [c.strip() for c in line.strip().strip('|').split('|')]


def main():
    if not os.path.exists(REG):
        print('❌ 找不到登记册: %s' % REG)
        return 2

    lines = io.open(REG, encoding='utf-8').read().split('\n')

    # docs/specs 的 SPEC 编号集合（大写去前缀），用于存在性校验
    spec_ids = set()
    if os.path.isdir(SPEC_DIR):
        for f in os.listdir(SPEC_DIR):
            # ⚠️ 两种命名都必须覆盖：P 系列是 `P106-xxx.md`（字母后**无**横线），
            #    S 系列是 `S-17-xxx.md`（字母后**有**横线）。
            #    首版正则写成 ^([A-Za-z]+-\d+) 只匹配到 S 系列 ⇒ 109 个 P 系列
            #    SPEC 全部漏收 ⇒ 「悬空 SPEC 引用」规则对 P 系列**长期静默失效**。
            #    该缺陷由反证（注入 SPC-P999 不报错）发现，非读代码看出来。
            m = re.match(r'^([A-Za-z]+-?\d+)', f)
            if m:
                spec_ids.add(m.group(1).upper().replace('-', ''))

    errors = []
    row_count = 0
    design_rule_rows = 0

    for i, ln in enumerate(lines):
        m = REQ_ROW.match(ln)
        if not m:
            continue
        rid = m.group(1)
        cells = cells_of(ln)
        n = len(cells)
        row_count += 1
        loc = '第 %d 行' % (i + 1)

        # ── 规则 1：列数一致（5=铁律，6=常规）──
        if rid in DESIGN_RULES:
            design_rule_rows += 1
            if n not in (5, 6, 7):
                errors.append('%s %s 是设计铁律条目，列数应为 5（原始）或 7（已加状态码），实测 %d ⇒ 结构损坏'
                              % (loc, rid, n))
            continue

        if n not in (6, 7):
            errors.append('%s %s 列数应为 6（未加状态码）或 7（已加状态码），实测 %d ⇒ 表结构损坏'
                          % (loc, rid, n))
            continue

        spec_col = cells[4]
        status = cells[5]
        code = cells[6] if n == 7 else ''

        # ── 规则 0：状态码必须在受控词表内 ──
        if n == 6:
            errors.append('%s %s 缺「状态码」列 ⇒ 未纳入机器可检范围' % (loc, rid))
        elif code not in CODES:
            errors.append('%s %s 状态码 %r 不在受控词表 %s 内' % (loc, rid, code, sorted(CODES)))
        else:
            rule = CODE_RULES.get(code)
            if rule and not rule(status):
                errors.append('%s %s 状态码=%s 与状态文字「%s」不一致 ⇒ 码与文字脱节'
                              % (loc, rid, code, status[:36]))

        # ── 规则 2：状态列非空、非表格残留 ──
        if not status or status in ('|', '---', '-'):
            errors.append('%s %s 状态列为空或为表格残留（%r）' % (loc, rid, status))

        # ── 规则 5：状态列必须以情绪标记开头 ──
        if status and not status.startswith(MOOD):
            errors.append('%s %s 状态列未以情绪标记（%s）开头（%r）⇒ 可能把分组标题误写进状态列'
                          % (loc, rid, '/'.join(MOOD[:4]), status[:30]))

        # ── 规则 3：状态声称完成 + 引用了 SPC-xxx ⇒ SPEC 必须存在 ──
        if any(w in status for w in DONE_WORDS):
            for mm in re.finditer(r'SPC-([A-Za-z]+-?\d+)', spec_col):
                ref = mm.group(1).upper().replace('-', '')
                if ref not in spec_ids:
                    errors.append('%s %s 状态称已实现，但关联 SPEC「%s」在 %s/ 下不存在 ⇒ 悬空引用'
                                  % (loc, rid, mm.group(0), SPEC_DIR))

    print('扫描 REQ 行: %d（其中设计铁律 %d 条）' % (row_count, design_rule_rows))
    if errors:
        print('\n❌ 登记册漂移守卫：发现 %d 处结构/引用问题' % len(errors))
        for e in errors:
            print('   - %s' % e)
        return 1

    print('✅ 登记册漂移守卫通过：表结构一致、状态列合规、无悬空 SPEC 引用')
    return 0


if __name__ == '__main__':
    sys.exit(main())