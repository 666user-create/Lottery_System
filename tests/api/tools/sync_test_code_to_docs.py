# -*- coding: utf-8 -*-
"""
把接口测试源码同步进《明细/接口测试/分册》文档的「五、测试代码」章节。

为什么需要这个工具：
    分册文档是给人读的复习材料，需要**完整源码 + 走读注释**；
    但源码会变，手抄必然脱节。所以由脚本从 `tests/api/test_*.py` 抽取源码贴进文档，
    改完代码重跑本脚本即可刷新（幂等：重复执行不会叠加）。

用法：
    cd tests/api
    python tools/sync_test_code_to_docs.py            # 同步（写入文档）
    python tools/sync_test_code_to_docs.py --check    # 只校验：源码每一行是否都进了文档

抽取规则：
    模块头（含模块 docstring 与 import）→ 独立段落；
    每个测试类：类头（装饰器 + 类 docstring）一段，其下每个用例方法一段；
    模块级工具函数（如 _fetch_page）→ 独立段落。
    每段代码前会生成「这段在做什么 / 依赖 fixture / 关键断言 / 标记」的走读块。
"""
import argparse
import ast
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

API_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))       # tests/api
DOC_DIR = r"D:\java\lottery_system\docs\test\明细\接口测试"  # 2026-10-20：随 V4.0 文档重构迁移（原 docs\test\02-接口测试-NN-*.md 已拆入本目录）

# 分册编号 ↔ 测试文件（与文档文件名严格一致，缺一不可）
DISPATCH = [
    ("01-用户与认证", "test_01_auth_keypath.py"),
    ("02-活动模块", "test_02_activity_keypath.py"),
    ("03-奖品模块", "test_03_prize_keypath.py"),
    ("04-抽奖与中奖记录", "test_04_draw_keypath.py"),
    ("05-AI客服", "test_05_ai_keypath.py"),
    ("06-FAQ与工单", "test_06_faq_handover_keypath.py"),
    ("07-权限矩阵与接口契约", "test_07_contract_keypath.py"),
    ("08-分页边界", "test_08_boundary_paging.py"),
    ("09-输入边界", "test_09_boundary_input.py"),
    ("10-异常与畸形请求", "test_10_exception_malformed.py"),
    ("11-并发", "test_11_concurrency.py"),
    ("12-依赖降级", "test_12_dependency.py"),
    ("13-结果正确性", "test_13_result_correctness.py"),
    ("14-缺陷防退化", "test_14_bugfix_regression.py"),
    ("15-数据统计", "test_15_stat.py"),
    ("16-操作日志", "test_16_operation_log.py"),
    ("17-RAG诊断", "test_17_rag_diagnose.py"),
    ("18-存量接口补齐", "test_18_gap_coverage.py"),
]

SECTION_MARK = "\n## 五、测试代码"
LEGACY_TAIL = "\n---\n\n### 本册覆盖范围说明"


def node_span(node):
    """返回节点源码的起止行（含装饰器；AST 的 lineno 不含装饰器，需取 min）"""
    start = node.lineno
    for dec in getattr(node, "decorator_list", []):
        start = min(start, dec.lineno)
    return start, node.end_lineno


def slice_lines(lines, node):
    s, e = node_span(node)
    return "\n".join(lines[s - 1:e])


def decorator_text(node):
    return ["@" + ast.unparse(d) for d in getattr(node, "decorator_list", [])]


def story_of(node):
    for d in getattr(node, "decorator_list", []):
        src = ast.unparse(d)
        m = re.search(r"\.(story|feature)\((.*?)\)", src)
        if m:
            return m.group(2).strip("'\"")
    return ""


def fixtures_of(fn):
    return [a.arg for a in fn.args.args if a.arg != "self"]


def assertions_of(fn):
    """抽取断言要点：优先用 assert 的自定义提示语，其次用表达式本身"""
    out = []
    for node in ast.walk(fn):
        if isinstance(node, ast.Assert):
            text = None
            if node.msg is not None:
                try:
                    val = ast.literal_eval(node.msg)
                    if isinstance(val, str):
                        text = val
                except Exception:
                    text = None
            if text is None:
                text = ast.unparse(node.test)
            text = " ".join(str(text).split())
            if len(text) > 110:
                text = text[:110] + "…"
            if text not in out:
                out.append(text)
        if len(out) >= 3:
            break
    return out


def calls_of(fn):
    """从用例体里抽出实际调用的接口路径（字符串字面量中以 / 开头的项）"""
    out = []
    for node in ast.walk(fn):
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            v = node.value
            if v.startswith("/") and len(v) > 2 and " " not in v:
                if v not in out:
                    out.append(v)
    return out[:4]


def refs_of(text):
    """从文档字符串里抽出关联的契约编号（C-x）与缺陷编号（BUG-xxx）"""
    contracts = sorted(set(re.findall(r"C-\d+", text or "")))
    bugs = sorted(set(re.findall(r"BUG-\d+", text or "")))
    return contracts, bugs


def marks_of(node):
    marks = []
    for d in getattr(node, "decorator_list", []):
        src = ast.unparse(d)
        for key in ("smoke", "keypath", "boundary", "reverse", "perf"):
            if f"mark.{key}" in src and key not in marks:
                marks.append(key)
    return marks


def build_segments(path):
    """把测试文件切成「段落」列表：每段含标题、走读说明、原始代码"""
    src_text = open(path, encoding="utf-8").read()
    lines = src_text.splitlines()
    tree = ast.parse(src_text)
    segments = []

    # ---- 模块头：模块 docstring + 全部 import + 模块级常量 ----
    # 定义：第一个「类/函数」节点之前的所有内容都算文件头
    # （模块 docstring、import、模块级常量/赋值都在此范围内）
    first_def = None
    for n in tree.body:
        if isinstance(n, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
            first_def = n
            break
    head_end = (node_span(first_def)[0] - 1) if first_def else len(lines)
    if head_end > 0:
        segments.append({
            "title": "文件头：模块说明与导入",
            "kind": "header",
            "doc": (ast.get_docstring(tree) or "").strip(),
            "meta": [],
            "code": "\n".join(lines[:head_end]),
        })

    for node in tree.body:
        if isinstance(node, ast.ClassDef):
            # 类头 = 类起点到第一个方法起点之前
            methods = [m for m in node.body
                       if isinstance(m, ast.FunctionDef) and m.name.startswith("test")]
            others = [m for m in node.body
                      if isinstance(m, ast.FunctionDef) and not m.name.startswith("test")]
            first_member = None
            for m in node.body:
                if isinstance(m, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    first_member = m
                    break
            class_start = node_span(node)[0]
            head_stop = (node_span(first_member)[0] - 1) if first_member else node_span(node)[1]
            segments.append({
                "title": f"测试类 `{node.name}`",
                "kind": "class",
                "doc": (ast.get_docstring(node) or "").strip(),
                "meta": ([f"allure story：{story_of(node)}"] if story_of(node) else [])
                        + ([f"共同标记：{'、'.join(marks_of(node))}"] if marks_of(node) else [])
                        + ([f"用例 {len(methods)} 个"] if methods else []),
                "code": "\n".join(lines[class_start - 1: head_stop]),
            })
            for m in methods:
                doc = (ast.get_docstring(m) or "").strip()
                contracts, bugs = refs_of(doc)
                calls = calls_of(m)
                segments.append({
                    "title": f"用例 `{m.name}`",
                    "kind": "method",
                    "doc": doc,
                    "meta": ([f"依赖 fixture：{'、'.join(fixtures_of(m))}"] if fixtures_of(m) else [])
                            + ([f"调用接口：{'、'.join('`' + c + '`' for c in calls)}"] if calls else [])
                            + ([f"关联契约：{'、'.join(contracts)}"] if contracts else [])
                            + ([f"关联缺陷：{'、'.join(bugs)}"] if bugs else [])
                            + ([f"标记：{'、'.join(marks_of(m))}"] if marks_of(m) else []),
                    "asserts": assertions_of(m),
                    "code": slice_lines(lines, m),
                })
            for m in others:      # 类内非 test 辅助方法（如有）
                segments.append({
                    "title": f"类内辅助 `{node.name}.{m.name}`",
                    "kind": "helper",
                    "doc": (ast.get_docstring(m) or "").strip(),
                    "meta": [],
                    "code": slice_lines(lines, m),
                })
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            segments.append({
                "title": f"模块级函数 `{node.name}`",
                "kind": "helper",
                "doc": (ast.get_docstring(node) or "").strip(),
                "meta": [],
                "code": slice_lines(lines, node),
            })
    return segments


def render_section(fname, segments):
    out = [
        "",
        "## 五、测试代码（完整源码，逐段走读）",
        "",
        f"> 本节由 `tests/api/tools/sync_test_code_to_docs.py` 自动同步自 `tests/api/{fname}`，"
        "**完整无删减**（含模块头、fixture 依赖、每条断言）。改完代码重跑该脚本即可刷新。",
        "> 每个代码块上方的引用块是「这段在做什么」，便于快速复习；断言细节见第四节。",
        "",
    ]
    for idx, seg in enumerate(segments, start=1):
        out.append(f"### 5.{idx} {seg['title']}")
        out.append("")
        detail = []
        if seg.get("doc"):
            detail.append(seg["doc"])
        if seg.get("meta"):
            detail.append(" ｜ ".join(seg["meta"]))
        if seg.get("asserts"):
            detail.append("**关键断言**：\n" + "\n".join(f"- `{a}`" for a in seg["asserts"]))
        if detail:
            # 逐行加引用前缀，空行用单个 ">" 保持引用块连续（不要二次加前缀，否则会出现 "> >"）
            body = "\n\n".join(detail)
            quoted = "\n".join(("> " + ln) if ln.strip() else ">" for ln in body.splitlines())
            out.append(quoted)
            out.append("")
        out.append("```python")
        out.append(seg["code"].rstrip())
        out.append("```")
        out.append("")
    return "\n".join(out)


def strip_old_section(text):
    """幂等：去掉上一次生成的第五章（以及已被源码取代的模块 docstring 附录）"""
    if SECTION_MARK in text:
        text = text.split(SECTION_MARK)[0].rstrip() + "\n"
    if LEGACY_TAIL in text:
        text = text.split(LEGACY_TAIL)[0].rstrip() + "\n"
    # 若结尾只剩一个孤立的 --- 分隔线，一并去掉
    text = re.sub(r"\n---\s*\n\s*$", "\n", text)
    return text


def sync(write=True):
    problems = []
    for title, fname in DISPATCH:
        doc_path = os.path.join(DOC_DIR, f"{title}.md")
        src_path = os.path.join(API_DIR, fname)
        if not os.path.exists(doc_path):
            problems.append(f"缺文档：{doc_path}")
            continue
        if not os.path.exists(src_path):
            problems.append(f"缺源码：{src_path}")
            continue

        segments = build_segments(src_path)
        base = strip_old_section(open(doc_path, encoding="utf-8").read())
        new_text = base + render_section(fname, segments)
        if write:
            open(doc_path, "w", encoding="utf-8").write(new_text)

        # ---- 覆盖校验：源码每一非空行都必须出现在文档的代码块里 ----
        code_lines = re.findall(r"```python\n(.*?)\n```", new_text, re.S)
        blob = "\n".join(code_lines)
        blob_lines = {ln.strip() for ln in blob.splitlines() if ln.strip()}
        missing = [ln for ln in open(src_path, encoding="utf-8").read().splitlines()
                   if ln.strip() and ln.strip() not in blob_lines]
        status = "OK" if not missing else f"缺 {len(missing)} 行"
        print(f"  [{'写入' if write else '校验'}] {title}.md ← {fname}  "
              f"（{len(segments)} 段 / 源码 {len(open(src_path, encoding='utf-8').read().splitlines())} 行） {status}")
        if missing:
            problems.append(f"{fname} 有 {len(missing)} 行未进文档，例如：{missing[:3]}")
    return problems


def main():
    ap = argparse.ArgumentParser(description="把接口测试源码同步进分册文档")
    ap.add_argument("--check", action="store_true", help="只校验，不写文件")
    args = ap.parse_args()
    problems = sync(write=not args.check)
    print()
    if problems:
        print("存在问题：")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print(f"全部 {len(DISPATCH)} 册同步完成，源码逐行覆盖校验通过 ✅")


if __name__ == "__main__":
    main()
