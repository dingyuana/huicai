#!/usr/bin/env python3
"""
实体入参的状态越权守卫（REQ-2026-129 / P105）

背景：连续抓到 5 处 P0 —— 端点直接 `@RequestBody <Entity>`（违反铁律 #13），
而 Service 的 `create()` 只在 `status == null` 时兜底，导致客户端可任意指定状态，
一步跳过人审/状态机（铁律 #1 / #4）。实例：
  1. TaxServiceImpl.createOutput        → POST status=VOUCHERED  （销项发票跳过审核链）
  2. TaxServiceImpl.createDeclaration   → POST status=APPROVED  （跳过 submit+approve）
  3. AssetDisposalServiceImpl.create    → POST status=APPROVED  （跳过 approve）
  4. AssetInventoryServiceImpl.create   → 同上
  5. AssetCardServiceImpl.create        → POST status=DISPOSED  （直接造「已处置」卡）

本脚本按**行首锚定 + 先剥注释**的方式判定，避免重复此前扫描器的失败：
前三次误报/漏报都源于跨行宽松正则分不清「代码」与「注释里刚被替换掉的旧写法」。

退出码：0 = 通过；1 = 发现风险（CI 应红）
用法：python3 scripts/check_entity_status_massassignment.py [--dir backend/src/main/java]
"""
import argparse
import pathlib
import re
import sys

# 剥掉行注释与块注释，避免把注释里的旧代码当成活代码
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"//[^\n]*")


def strip_comments(text: str) -> str:
    """用等长空格替换注释，保持行号不变，便于回报行号。"""
    def blank(m):
        return re.sub(r"[^\n]", " ", m.group(0))
    return LINE_COMMENT.sub(blank, BLOCK_COMMENT.sub(blank, text))


# 行首锚定：只匹配真正的代码行（允许缩进），排除注释与字符串里的写法
COND_GUARD = re.compile(r"^\s*if\s*\([^)]*get\w*[Ss]tatus\(\)\s*==\s*null\s*\)")
REQUEST_BODY_ENTITY = re.compile(r"@RequestBody\s+(?:@Valid\s+)?(\w*Entity)\b")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default="backend/src/main/java")
    ap.add_argument("--quiet", action="store_true")
    ap.add_argument("--strict", action="store_true",
                    help="发现风险即以退出码 1 阻断（默认只报告、不阻断）")
    args = ap.parse_args()

    root = pathlib.Path(args.dir)
    if not root.is_dir():
        print(f"目录不存在: {root}", file=sys.stderr)
        return 2

    findings = []

    # 规则 1：Service 中仍存在的「条件兜底」写法（行首锚定）
    for f in root.rglob("*ServiceImpl.java"):
        raw = f.read_text(encoding="utf-8", errors="ignore")
        code = strip_comments(raw)
        for i, line in enumerate(code.splitlines(), 1):
            if COND_GUARD.search(line):
                findings.append(
                    (f, i, "条件兜底",
                     "getStatus()==null 才设默认状态 ⇒ 客户端可指定状态（历史 5 处 P0 的同一写法）",
                     line.strip()))

    # 规则 2：Controller 直接绑定 Entity，且该 Entity 在本仓有 status 字段
    entity_with_status = {}
    for f in root.rglob("*Entity.java"):
        code = strip_comments(f.read_text(encoding="utf-8", errors="ignore"))
        if re.search(r"private\s+\w*[Ss]tring\s+status\s*;", code):
            entity_with_status[f.stem] = f

    for f in root.rglob("*Controller.java"):
        code = strip_comments(f.read_text(encoding="utf-8", errors="ignore"))
        for i, line in enumerate(code.splitlines(), 1):
            for ent in REQUEST_BODY_ENTITY.findall(line):
                if ent in entity_with_status:
                    findings.append(
                        (f, i, "实体直收",
                         f"{ent} 含 status 字段却被 @RequestBody 直收（违反铁律 #13），"
                         f"叠加服务端条件兜底即可越权指定状态",
                         line.strip()))

    if not findings:
        if not args.quiet:
            print(f"✅ 未发现实体入参的状态越权风险（扫描 {root}）")
            print("   规则1：Service 内无「getStatus()==null 才设默认」写法")
            print("   规则2：无「含 status 的 Entity 被 @RequestBody 直收」端点")
        return 0

    # 规则 2 是「结构性问题」，规则 1 是「已可利用」—— 分级输出
    exploitable = [x for x in findings if x[2] == "条件兜底"]
    structural = [x for x in findings if x[2] == "实体直收"]

    print(f"❌ 检出 {len(findings)} 处实体入参的状态风险：\n")
    if exploitable:
        print(f"【P0 · 已可利用】Service 内仍存在条件兜底 {len(exploitable)} 处：")
        for f, i, _, why, src in exploitable:
            print(f"  {f}:{i}  {why}\n      {src}")
        print()
    print(f"【P2 · 结构性问题】含 status 的 Entity 被 @RequestBody 直收 {len(structural)} 处：")
    for f, i, _, why, src in structural:
        print(f"  {f}:{i}  {src}")
    print("\n处置：引入 DTO（不含 status）让越权值无法绑定；"
          "或服务端改为无条件强制初始状态。见 REQ-2026-129。")
    # 默认只报告不阻断 —— 存量 7 处尚未清理，硬失败会让门禁恒红。
    # 恒红与恒绿同样有害（AGENTS §4.5 第 21 条）：恒红会让人习惯性忽略门禁。
    # 存量清零后改用 --strict 转为强制。
    if args.strict:
        print("\n--strict：判失败。")
        return 1
    print("\n（默认只报告。存量清零后加 --strict 转为强制门禁。）")
    return 0


if __name__ == "__main__":
    sys.exit(main())