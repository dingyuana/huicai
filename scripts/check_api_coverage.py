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

🔴 2026-10-09：那个 280 的基线本身是**错的**，本轮把测量口径修对后重测为 92。
  首版有 5 处漏扫，每一处的症状都是「成片误报且不报任何错」：
    ① 前端正则取 `group(1)`（HTTP 方法名）而非 `group(2)`（URL）
       —— 而 group(1) 恰为 'get'/'post'/'put'/'delete'，又被 normalize() 的噪声
       过滤丢弃 ⇒ 单引号/双引号写法的调用**整体漏扫**（源码 489 处，脚本只认 148）。
    ② 前端只扫 `src/api/**/*.ts`，不扫 `.vue`（本项目 43 处调用直接写在页面里）。
    ③ 后端未剥离 javadoc —— `{@code @PostMapping}` 之类会污染「当前类前缀」。
    ④ 后端只认 `@XxxMapping("path")`，漏掉无参 `@XxxMapping`
       （= 类级前缀本身就是一个端点，如 `PeriodController` 的 `@PostMapping`）。
    ⑤ 后端漏掉 `@XxxMapping(value = "/path", consumes = ...)` 形态（本项目 3 处）。
  **判据沉淀**：门禁的阈值必须用「门禁自己的口径」测量，不能用另一套口径自算；
  「阈值很紧」与「阈值测错了」可以同时成立 —— 后者更危险，因为它让人不敢下调。
  另：修好口径后孤儿从 53 → 0 的过程中，暴露并修掉了 **16 处真实前端 404**
  （路径缺前缀 / 指向后端不存在的端点），即这道门禁此前一直在对真缺陷装聋。
"""

import argparse
import re
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
BACKEND_DIR = PROJECT_ROOT / "backend"
FRONTEND_DIR = PROJECT_ROOT / "frontend"

# 棘轮基线：2026-10-09 口径修正后实测
# （后端 471 端点 / 前端 390 调用 / 匹配 379 / 未接 92 / 孤儿 0）
DEFAULT_MAX_UNCOVERED = 92
DEFAULT_MAX_ORPHAN = 0


COMMENT_RE = re.compile(r'/\*.*?\*/', re.S)


def _strip_comments(text):
    """去掉块注释与行注释 —— 否则 javadoc 里的 {@code @GetMapping("/x")} 会被当成真端点.

    ⚠️ 本项目 `EnterpriseWriteGuard` 的类注释里就写着
    {@code @PostMapping}/{@code @PutMapping}/{@code @DeleteMapping}，
    首版未剥注释时它会污染「当前类前缀」，进而把后续方法的路径算错。
    """
    text = COMMENT_RE.sub(' ', text)
    return re.sub(r'//[^\n]*', ' ', text)


# `@GetMapping` / `@GetMapping()` / `@GetMapping("path")` / `@GetMapping(value = "path", ...)`
# 四种形态都要认。漏掉任一种的症状都一样：**成片误报，且不报任何错**。
#   - 漏前两种 ⇒「类级前缀本身就是一个端点」的 POST/PUT/GET 消失
#     （如 PeriodController 的 `@PostMapping` = POST /api/v1/periods），
#     前端 `request.post('/v1/periods', data)` 被误判成「前端调了不存在的端点」。
#   - 漏第三种 ⇒ 本项目 3 处 `@PostMapping(value = "/import", consumes = ...)`
#     （Customer/Vendor/Subject 导入）消失。
# ⚠️ 末尾必须允许「还有别的参数」，否则
# `@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`
# 匹配失败后会退化成「无参形态」⇒ 误报成 `/api/v1/customers`（凭空多出一个端点）。
MAPPING_ARG = (r'(?:\(\s*(?:(?:value|path)\s*=\s*)?'
               r'(?:"([^"]*)"|\'([^\']*)\')?'
               r'\s*(?:,[^)]*)?\s*\))?')


def extract_backend_endpoints():
    """提取后端所有 Controller 端点."""
    endpoints = []

    controller_files = sorted(BACKEND_DIR.glob("src/main/java/**/*Controller.java"))

    for fpath in controller_files:
        content = _strip_comments(fpath.read_text(encoding="utf-8"))
        current_ctrl = ""
        for line in content.split("\n"):
            # 类级 @RequestMapping（同样支持 value = 形态）
            m = re.search(r'@RequestMapping\s*\(\s*(?:(?:value|path)\s*=\s*)?["\']([^"\']+)["\']', line)
            if m:
                current_ctrl = m.group(1)

            # 方法级注解（含无参与 value= 形态）
            for method in ["PostMapping", "GetMapping", "PutMapping", "DeleteMapping"]:
                for m2 in re.finditer(r'@' + method + r'\b' + MAPPING_ARG, line):
                    sub = m2.group(1) if m2.group(1) is not None else (
                        m2.group(2) if m2.group(2) is not None else "")
                    full = current_ctrl + ("/" if sub and not sub.startswith("/") else "") + sub
                    endpoints.append(full)

    return sorted(set(endpoints))


def extract_frontend_api_calls():
    """提取前端所有 API 调用.

    🔴 扫描范围必须覆盖 `.vue`（AGENTS §4.5 第 22 条：多份配置只读其一 ⇒ 成片误报）

    首版只扫 `src/api/**/*.ts`，而本项目有 **43 处 `request.*` 直接写在 `.vue` 里**
    （`views/**/*.vue`，实测 22 个后端端点因此被误判为「前端未接」）。
    症状极具欺骗性：这批端点**确实已被前端调用**，但门禁报红；
    若照着报告去「接入前端」，会发现页面里已经有了 —— 而真正的倒退
    （新增端点未接）会被这条噪声淹没。

    故此处同时扫 `src/api/**/*.ts` 与 `src/**/*.vue`，并支持双引号字面量。
    """
    calls = []

    api_files = sorted(FRONTEND_DIR.glob("src/api/**/*.ts"))
    vue_files = sorted(FRONTEND_DIR.glob("src/**/*.vue"))

    for fpath in api_files + vue_files:
        content = fpath.read_text(encoding="utf-8")
        # 三种字面量形态，统一取 **group(2)**（group(1) 是 HTTP 方法名，不是 URL）
        # ⚠️ 首版在此处写的是 group(1)，而 group(1) 恒为 'get'/'post'/'put'/'delete'，
        #   又恰被 normalize() 的噪声过滤（`if not p or p in ("get","post",...)`）丢弃
        #   ⇒ **所有单引号/双引号写法的 API 调用被整体漏扫**，而模板字符串
        #   （反引号，写的是 group(2)）正常计入。
        #   症状：报告称「前端调用 148 条」，而实测源码里有 489 处 `request.*`。
        #   判据：正则里 `(get|post|put|delete)` 是 group(1)，URL 一定是 group(2)。
        # 模板字符串: request.post(`/tax/output-invoices/${id}/confirm`)
        for m in re.finditer(r"request\.(get|post|put|delete)\s*\(\s*`([^`]+)`", content):
            calls.append(m.group(2))
        # 普通字符串: request.get('/tax/types/page') / request.get("/tax/types/page")
        for m in re.finditer(r"request\.(get|post|put|delete)\s*\(\s*'([^']+)'", content):
            calls.append(m.group(2))
        for m in re.finditer(r'request\.(get|post|put|delete)\s*\(\s*"([^"]+)"', content):
            calls.append(m.group(2))

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
