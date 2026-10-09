#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
资产负债表存量脏数据体检脚本（REQ-2026-082 的运营前置）—— **只读，绝不写库**

## 为什么需要它

REQ-082（资产负债表平衡根治）登记状态为「算法层已实现待验收，**部署与存量脏数据为运营项**」。
算法层（`ReportServiceImpl#buildBalanceSheet`）已给出 `diff` 与 `unbalancedItems`，
但那要**先起应用、先进页面**才看得到；而验收方真正需要的是一张
「哪几个科目、按什么口径、差多少钱、该怎么处理」的清单。

本脚本把这套判定搬到 SQL 层直接跑，**不启动应用、不修改任何数据**。

## 🔴 三条硬约束（本脚本绝不越过）

1. **只读**：只 `SELECT`。无 `INSERT/UPDATE/DELETE/DDL`，无 TRUNCATE。
2. **不重算账**：不生成/修改凭证，不动 `t_subject_balance`。
   资产负债表取的是 `t_subject_balance` 的既有余额，脚本只做**分类与加总**。
3. **不猜「正确的数是多少」**：只报「差额」与「可疑科目」，**不给修正建议的具体金额**——
   差额该怎么处理是业务判断（补期初？补结转？红冲？），脚本无权替业务方决定。
   ⚠️ 这正是 AGENTS §4.5 第 39 条的形态：把「诊断」伪装成「修复」。

## 判定口径必须与生产一致

分类逻辑逐条对齐 `ReportServiceImpl#buildBalanceSheet`（`build_balance_sheet` 函数）：
- 段位 `code.charAt(0)`：1/5→资产，2→负债，3→按符号二分，4→权益（4103/4104 单列），
  6→当期损益（仅期末列口径），其它→`unclassified`
- 方向换算：资产段 `debit? +b : -b`；负债/权益段 `credit? +b : -b`
- 权益合计 = 4103 + 4104 + 当期 6xx 净额（贷方合计 − 借方合计）
- `diff = 资产总计 − (负债总计 + 权益合计)`，容差 0.01
- ⚠️ **年初列另算一遍**（`yearStartOf(period)` 取当年 01 期的期初余额），
  因为生产代码对年初/期末用同一分类逻辑但不同聚合基数（`ReportServiceImpl:171`）

## 用法

    python3 scripts/diagnose_balance_sheet_dirty.py                  # 用默认连接串
    python3 scripts/diagnose_balance_sheet_dirty.py --period 202609
    python3 scripts/diagnose_balance_sheet_dirty.py --dsn "postgresql://user:pw@host:5432/db"
    python3 scripts/diagnose_balance_sheet_dirty.py --enterprise-id 1

退出码：0 = 平衡（或差额在容差内）；1 = 不平衡；2 = 环境/连接问题。
⚠️ **退出码只反映「是否不平衡」，不代表门禁用途** ——
   数据不平衡是**运营事实**而非代码缺陷，故本脚本**不挂 CI**（§4.5 第 21 条：恒红无意义）。
"""
import argparse
import os
import sys
from decimal import Decimal

TOLERANCE = Decimal("0.01")

# ── 与 ReportServiceImpl 一致的分类常量 ──
PROFIT_4103 = "4103"   # 本年利润
PROFIT_4104 = "4104"   # 利润分配-未分配利润

SQL_SUBJECT_BALANCE = """
SELECT s.id            AS subject_id,
       s.code,
       s.name,
       s.direction,
       s.account_type,
       COALESCE(sb.begin_balance, 0)  AS begin_balance,
       COALESCE(sb.debit_total, 0)    AS debit_total,
       COALESCE(sb.credit_total, 0)   AS credit_total,
       COALESCE(sb.end_balance, 0)    AS end_balance
FROM t_subject s
LEFT JOIN t_subject_balance sb ON sb.subject_id = s.id AND sb.period = %s
WHERE s.deleted = 0 AND s.is_active = TRUE
ORDER BY s.code
"""

# 当期损益（6xx）净额：贷方 − 借方。与 SQL 里的 incomeStatementData 同口径地
# 只用「已过账凭证」，排除结转/分配凭证。
SQL_PERIOD_PROFIT = """
SELECT COALESCE(SUM(e.credit - e.debit), 0) AS net
FROM t_voucher_entry e
INNER JOIN t_voucher v ON v.id = e.voucher_id
INNER JOIN t_subject s ON s.id = e.subject_id
WHERE v.deleted = 0 AND v.status = 'POSTED'
  AND v.period = %s
  AND s.code LIKE '6%%'
  AND v.voucher_no NOT LIKE 'CLOSE-%%' AND v.voucher_no NOT LIKE 'DISTRIB-%%'
"""

# 「无隔离/无归属」的科目余额行：这些行不属于任何账套，多半是脏数据来源。
SQL_ORPHAN_BALANCE = """
SELECT COUNT(*) AS cnt, COALESCE(SUM(sb.end_balance), 0) AS amount
FROM t_subject_balance sb
WHERE sb.enterprise_id IS NULL
"""

SQL_MISSING_BALANCE_ROWS = """
SELECT COUNT(*) FROM t_subject s
WHERE s.deleted = 0 AND s.is_active = TRUE
  AND NOT EXISTS (SELECT 1 FROM t_subject_balance sb
                  WHERE sb.subject_id = s.id AND sb.period = %s)
"""


def dec(v):
    if v is None:
        return Decimal("0")
    return Decimal(str(v))


def classify(rows, period_profit, use_begin):
    """复刻 buildBalanceSheet 的分类与加总。返回 (资产, 负债, 权益, 未分类项)。

    `use_begin=True` 走年初列（BEGIN_BALANCE + 不计当期损益），
    否则走期末列（END_BALANCE + 计当期损益 6xx 净额）。
    """
    field = "begin_balance" if use_begin else "end_balance"
    assets = liab = equity = Decimal("0")
    p4103 = p4104 = Decimal("0")
    bad = []

    for r in rows:
        code = (r["code"] or "").strip()
        if not code:
            continue
        direction = (r["direction"] or "").strip().lower()
        balance = dec(r[field])
        top = code[0]

        if top in "123456" and not direction:
            bad.append((code, r["name"], balance, "missing-direction（1~6 段必须有方向）"))
            continue

        if top == "1" or top == "5":
            signed = balance if direction == "debit" else -balance
            assets += signed
        elif top == "2":
            signed = balance if direction == "credit" else -balance
            liab += signed
        elif top == "3":
            signed = balance if direction == "debit" else -balance
            if signed >= 0:
                assets += signed
            else:
                liab += signed
        elif top == "4":
            signed = balance if direction == "credit" else -balance
            if code == PROFIT_4103:
                p4103 = signed
            elif code == PROFIT_4104:
                p4104 = signed
            else:
                equity += signed
        elif top == "6":
            # 年初口径无「本期」概念：6xx 属当期损益，计入年初会重复。
            pass
        else:
            bad.append((code, r["name"], balance, "unclassified（段位不在 1~6）"))

    if not use_begin:
        equity += period_profit

    total_equity = equity + p4103 + p4104
    total_liab_equity = liab + total_equity
    diff = (assets - total_liab_equity).quantize(Decimal("0.01"))
    return assets, liab, total_equity, diff, bad, p4103, p4104


def main():
    ap = argparse.ArgumentParser(description="资产负债表存量脏数据体检（只读）")
    ap.add_argument("--dsn", default=os.environ.get("HUICAI_DB_DSN"),
                    help="PostgreSQL 连接串；缺省读环境变量 HUICAI_DB_DSN")
    ap.add_argument("--period", default="202609", help="会计期间 yyyyMM（默认 202609）")
    ap.add_argument("--enterprise-id", type=int, default=None,
                    help="仅提示用；本脚本按 t_subject 全量口径计算，与应用的企业过滤不同")
    args = ap.parse_args()

    dsn = args.dsn
    if not dsn:
        env = os.environ.get("SPRING_DATASOURCE_URL") or ""
        if env.startswith("jdbc:"):
            dsn = "postgresql://" + env[len("jdbc:"):]
    if not dsn:
        print("❌ 未提供数据库连接串。用 --dsn 或设 HUICAI_DB_DSN / SPRING_DATASOURCE_URL。")
        print("   示例：--dsn \"postgresql://huicai_app:pw@localhost:5432/huicai\"")
        return 2

    try:
        import psycopg2  # noqa
    except ImportError:
        print("❌ 缺少 psycopg2。安装：pip install psycopg2-binary")
        return 2

    year_start = args.period[:4] + "01"
    try:
        conn = psycopg2.connect(dsn)
    except Exception as e:
        print("❌ 连接失败：%s" % e)
        return 2

    print("=" * 72)
    print("资产负债表存量脏数据体检（只读，不修改任何数据）")
    print("=" * 72)
    print("期间：%s   年初列口径期间：%s" % (args.period, year_start))
    if args.enterprise_id is not None:
        print("⚠️  本脚本按 t_subject 全量计算，**未按 enterprise_id 过滤**；"
              "多账套库的结果会与单账套页面不同。")
    print()

    cur = conn.cursor()
    try:
        # ── 1. 期末列 ──
        cur.execute(SQL_SUBJECT_BALANCE, (args.period,))
        rows_end = cur.fetchall()
        cols = [d[0] for d in cur.description]
        rows_end = [dict(zip(cols, r)) for r in rows_end]

        cur.execute(SQL_PERIOD_PROFIT, (args.period,))
        profit = dec(cur.fetchone()[0])

        a_end, l_end, e_end, diff_end, bad_end, p4103, p4104 = classify(rows_end, profit, False)

        # ── 2. 年初列 ──
        cur.execute(SQL_SUBJECT_BALANCE, (year_start,))
        rows_begin = cur.fetchall()
        rows_begin = [dict(zip(cols, r)) for r in rows_begin]
        a_beg, l_beg, e_beg, diff_beg, bad_beg, _, _ = classify(rows_begin, Decimal("0"), True)

        # ── 3. 数据卫生 ──
        cur.execute(SQL_ORPHAN_BALANCE)
        orphan_cnt, orphan_amt = cur.fetchone()
        cur.execute(SQL_MISSING_BALANCE_ROWS, (args.period,))
        missing_rows = cur.fetchone()[0]
    finally:
        cur.close()
        conn.close()

    # ── 输出 ──
    print("── 期末列（end_balance）" + "─" * 48)
    print("资产总计        %16s" % a_end)
    print("负债总计        %16s" % l_end)
    print("权益合计        %16s  （其中 4103=%s 4104=%s 当期损益=%s）"
          % (e_end, p4103, p4104, profit))
    print("负债+权益       %16s" % (l_end + e_end))
    print("差额 diff       %16s  %s" % (diff_end, "✅ 平衡" if abs(diff_end) < TOLERANCE else "❌ 不平衡"))
    print()

    print("── 年初列（%s 期初余额）" % year_start + "─" * 40)
    print("资产总计        %16s" % a_beg)
    print("负债总计        %16s" % l_beg)
    print("权益合计        %16s" % e_beg)
    print("差额 diff       %16s  %s" % (diff_beg, "✅ 平衡" if abs(diff_beg) < TOLERANCE else "❌ 不平衡"))
    print()

    unbalanced = abs(diff_end) >= TOLERANCE or abs(diff_beg) >= TOLERANCE

    if bad_end or bad_beg:
        print("── 未分类 / 缺方向的科目（生产代码会记入 unbalancedItems）" + "─" * 24)
        seen = set()
        for code, name, bal, reason in (bad_end + bad_beg):
            key = (code, reason)
            if key in seen:
                continue
            seen.add(key)
            print("  %-8s %-28s 余额=%14s  %s" % (code, (name or "")[:28], bal, reason))
        print()

    print("── 数据卫生" + "─" * 58)
    print("enterprise_id IS NULL 的余额行：%d 行，合计 end_balance=%s"
          % (orphan_cnt, orphan_amt))
    print("本期完全无余额记录的启用科目：%d 个" % missing_rows)
    print()

    if not unbalanced and not bad_end and not bad_beg and orphan_cnt == 0:
        print("✅ 未发现存量脏数据 —— REQ-082 的验收前置已满足。")
        return 0

    print("── 处置建议（脚本不代业务方决定金额，只指出问题）" + "─" * 26)
    if abs(diff_end) >= TOLERANCE:
        print("  1. 期末列差额 %s：逐科目核对下列方向，常见成因是" % diff_end)
        print("     ① 期初建账时资产/负债/权益三段未配平")
        print("     ② 损益类(6xx)未结转到 4103/4104（见上方「当期损益」是否非 0）")
        print("     ③ 往来科目挂错段位（如 1122 误设为 credit 方向）")
    if abs(diff_beg) >= TOLERANCE:
        print("  2. 年初列差额 %s：说明问题早于本期，优先查期初建账。" % diff_beg)
    if bad_end:
        print("  3. %d 个科目缺方向或段位非法 —— 生产代码已把它们排除在总计之外，"
              "须先补 t_subject.direction 再谈平衡。" % len({c for c, _, _, _ in bad_end}))
    if orphan_cnt:
        print("  4. %d 行余额的 enterprise_id 为 NULL —— 跨租户游离数据，"
              "会同时污染 RLS 与报表。" % orphan_cnt)
    if missing_rows:
        print("  5. %d 个启用科目本期无余额行 —— 若为新增科目属正常，"
              "若本应有余额则是期初未录。" % missing_rows)
    print()
    print("⚠️  以上仅为诊断。本脚本不修改任何数据；具体如何补期初/补结转/红冲，")
    print("    须由业务方按会计准则判断并留痕（AGENTS 铁律 #1：人是唯一审核主体）。")
    return 1


if __name__ == "__main__":
    sys.exit(main())