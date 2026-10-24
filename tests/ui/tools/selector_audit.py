# -*- coding: utf-8 -*-
"""
UI 自动化用例「选择器存活审计」工具
====================================

背景
----
前端页面在 2026-10-16 做过一次大幅改版（新增页面、重排 DOM、admin.html 改为
侧边栏 + iframe）。tests/ui 下 11 个用例类写在改版之前，其中的选择器是否还有效，
不能靠人工比对（171 个 id 逐个看眼睛会花），必须机器判定。

本工具做什么
------------
1. 从每个 *UiTest.java 中抽取所有 Selenium 定位表达式
   （By.id / By.name / By.cssSelector / By.className / By.tagName /
     By.linkText / By.partialLinkText / By.xpath）；
2. 从 src/main/resources/static 读取**全部** .html 与 .js（页面里内联 <script>
   动态生成的 DOM 也在这批文件里，所以「HTML 里没有但 JS 里拼出来」的元素同样能命中）；
3. 用「静态字符串命中」判定每个选择器是否还活着：
   - By.id    → 找 id="X" / id='X' / getElementById('X') / #X
   - By.name  → 找 name="X" / name='X'
   - css      → 简单选择器的 #id / .class / [name=..] / tag 分别降级匹配
   - class    → 找 class="... X ..." 或 classList.add('X')
   - xpath    → 抽取里面的 //tag 与 @id= / @class= 片段做启发式匹配
   - linkText → 在 HTML 文本中找该中文/英文串
4. 输出每个选择器的命中文件列表；未命中的单独成表（这些就是**必须重写**的位置）。

设计取舍
--------
- 用「静态字符串命中」而不是真的开浏览器逐个 findElement：
  真开浏览器要 20 秒/页 × 21 页，而且 JS 渲染依赖接口数据（无数据时表格为空，
  元素本轮不存在 → 假阴性）。静态审计的结论是「保守的」：静态没命中 = 一定有问题；
  静态命中 ≠ 运行时一定找得到（那属于运行时问题，交给真正的 test 跑）。
- 不引入第三方依赖，纯标准库。

用法
----
    cd tests/ui/tools
    $env:PYTHONIOENCODING="utf-8"
    python selector_audit.py

输出：out/selector-audit.md（人读）
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
STATIC = ROOT / "src" / "main" / "resources" / "static"
TESTS = Path(__file__).resolve().parents[1] / "src" / "test" / "java" / "com" / "qa" / "ui"
OUT_DIR = Path(__file__).resolve().parent / "out"


def load_corpus():
    """返回 {相对路径: 文本}，覆盖所有 html 与 js。"""
    files = {}
    for p in sorted(STATIC.rglob("*")):
        if p.suffix.lower() in (".html", ".js"):
            try:
                files[str(p.relative_to(STATIC)).replace("\\", "/")] = p.read_text(
                    encoding="utf-8", errors="replace"
                )
            except Exception as e:  # pragma: no cover
                print(f"  [跳过] {p}: {e}")
    return files


# 定位表达式抽取：By.xxx("....")  捕获 定位方式 + 字符串
BY_RE = re.compile(
    r"""By\.(id|name|cssSelector|className|tagName|linkText|partialLinkText|xpath)\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)"""
)
# open("/xxx.html")  页面路径
OPEN_RE = re.compile(r"""open\(\s*"([^"]+\.html)"\s*\)""")
# driver.get(...) 里显式写的路径
GET_RE = re.compile(r"""driver\.get\(\s*BASE_URL\s*\+\s*"([^"]+)"\s*\)""")
# admin.html 的 iframe 用法
FRAME_RE = re.compile(r"""switchTo\(\)\.frame\(\s*(?:"([^"]+)"|By\.id\("([^"]+)"\))\s*\)""")


def unescape_java(s: str) -> str:
    """处理 Java 字符串里的转义，够用即可。"""
    return s.replace('\\"', '"').replace("\\\\", "\\").replace("\\n", "\n")


def probe(by_kind: str, raw: str, corpus: dict):
    """
    返回命中文件列表。判定规则见文件头注释。
    """
    hits = []

    if by_kind == "id":
        pats = [
            re.compile(r"""id\s*=\s*["']%s["']""" % re.escape(raw)),
            re.compile(r"""getElementById\(\s*["']%s["']""" % re.escape(raw)),
            re.compile(r"""#%s\b""" % re.escape(raw)),
        ]
    elif by_kind == "name":
        pats = [
            re.compile(r"""name\s*=\s*["']%s["']""" % re.escape(raw)),
            re.compile(r"""getElementsByName\(\s*["']%s["']""" % re.escape(raw)),
            # 有些页面用 [name='x'] 选择器或 .find("[name='x']")
            re.compile(r"""\[\s*name\s*=\s*["']?%s["']?\s*\]""" % re.escape(raw)),
        ]
    elif by_kind == "className":
        pats = [
            re.compile(r"""class\s*=\s*["'][^"']*\b%s\b[^"']*["']""" % re.escape(raw)),
            re.compile(r"""classList\.(?:add|remove|toggle)\(\s*["']%s["']""" % re.escape(raw)),
            # class 由模板串拼出来的（如 status-${sc}）：退化为前缀匹配
            re.compile(r"""class\s*=\s*["'][^"']*%s""" % re.escape(raw)),
        ]
    elif by_kind == "tagName":
        pats = [re.compile(r"""<\s*%s[\s>/]""" % re.escape(raw), re.IGNORECASE)]
    elif by_kind == "cssSelector":
        pats = []
        sel = raw
        # 拆出 #id / .class / [name='x'] / tag，任意一种命中即算活
        for m in re.finditer(r"#([A-Za-z_][\w-]*)", sel):
            pats.append(re.compile(r"""id\s*=\s*["']%s["']""" % re.escape(m.group(1))))
            pats.append(re.compile(r"""#%s\b""" % re.escape(m.group(1))))
        for m in re.finditer(r"\.([A-Za-z_][\w-]*)", sel):
            pats.append(re.compile(r"""class\s*=\s*["'][^"']*\b%s\b""" % re.escape(m.group(1))))
        for m in re.finditer(r"""\[\s*([\w-]+)\s*=\s*["']?([^\]"']+)["']?\s*\]""", sel):
            pats.append(
                re.compile(
                    r"""%s\s*=\s*["']%s["']""" % (re.escape(m.group(1)), re.escape(m.group(2)))
                )
            )
        for m in re.finditer(r"(?:^|[\s>+~])([a-zA-Z][\w-]*)", sel):
            if m.group(1).lower() not in ("not", "nth", "first", "last"):
                pats.append(re.compile(r"""<\s*%s[\s>/]""" % re.escape(m.group(1)), re.IGNORECASE))
    elif by_kind == "linkText" or by_kind == "partialLinkText":
        pats = [re.compile(re.escape(raw))]
    elif by_kind == "xpath":
        pats = []
        for m in re.finditer(r"""@id\s*=\s*["']([^"']+)["']""", raw):
            pats.append(re.compile(r"""id\s*=\s*["']%s["']""" % re.escape(m.group(1))))
        for m in re.finditer(r"""@class\s*=\s*["']([^"']+)["']""", raw):
            pats.append(re.compile(r"""class\s*=\s*["'][^"']*%s""" % re.escape(m.group(1))))
        for m in re.finditer(r"""//([a-zA-Z][\w-]*)""", raw):
            pats.append(re.compile(r"""<\s*%s[\s>/]""" % re.escape(m.group(1)), re.IGNORECASE))
        # contains(text(),'xx')
        for m in re.finditer(r"""contains\(\s*(?:text\(\)\s*,\s*)?["']([^"']+)["']\)""", raw):
            pats.append(re.compile(re.escape(m.group(1))))
    else:
        pats = []

    if not pats:
        return None  # 无法判定（如纯结构 xpath），单独归类

    for name, text in corpus.items():
        for p in pats:
            if p.search(text):
                hits.append(name)
                break
    return sorted(set(hits))


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    corpus = load_corpus()
    print(f"静态语料: {len(corpus)} 个文件（html+js）")

    test_files = sorted(TESTS.glob("*UiTest.java"))
    print(f"用例文件: {len(test_files)} 个\n")

    per_file = {}
    total_sel = 0
    dead_total = 0

    for tf in test_files:
        src = tf.read_text(encoding="utf-8", errors="replace")
        sels = []
        for m in BY_RE.finditer(src):
            kind = m.group(1)
            raw = unescape_java(m.group(2))
            hits = probe(kind, raw, corpus)
            sels.append({"kind": kind, "expr": raw, "hits": hits})
        pages = sorted(set(OPEN_RE.findall(src)) | set(GET_RE.findall(src)))
        frames = sorted(
            set(a or b for a, b in FRAME_RE.findall(src))
        )
        # 去掉重复表达式
        seen = set()
        uniq = []
        for s in sels:
            k = (s["kind"], s["expr"])
            if k in seen:
                continue
            seen.add(k)
            uniq.append(s)

        dead = [s for s in uniq if s["hits"] == [] or s["hits"] is None]
        total_sel += len(uniq)
        dead_total += len(dead)
        per_file[tf.name] = {"selectors": uniq, "pages": pages, "frames": frames}

    # ---- 控制台 + markdown 输出 ----
    lines = []
    lines.append("# UI 用例选择器存活审计")
    lines.append("")
    lines.append("> 由 `tests/ui/tools/selector_audit.py` 生成。")
    lines.append(
        "> 判定方式：把每个定位表达式拿去匹配 `src/main/resources/static` 下全部 "
        "`.html` + `.js` 的文本（含内联脚本动态拼出的 DOM）。"
    )
    lines.append(
        "> **静态未命中 = 一定失效，必须重写**；静态命中 ≠ 运行时一定能找到"
        "（运行时还受接口数据、iframe、渲染时序影响）。"
    )
    lines.append("")
    lines.append("## 总览")
    lines.append("")
    lines.append("| 用例文件 | 定位表达式 | 静态失效 | 引用页面 | iframe |")
    lines.append("|---|---|---|---|---|")
    for fname, d in per_file.items():
        uniq = d["selectors"]
        dead = [s for s in uniq if not s["hits"]]
        lines.append(
            f"| `{fname}` | {len(uniq)} | **{len(dead)}** | "
            f"{len(d['pages'])} | {len(d['frames'])} |"
        )
    lines.append(
        f"| **合计** | **{total_sel}** | **{dead_total}** | | |"
    )
    lines.append("")

    lines.append("## 失效明细（必须重写）")
    lines.append("")
    for fname, d in per_file.items():
        dead = [s for s in d["selectors"] if not s["hits"]]
        if not dead:
            continue
        lines.append(f"### {fname} — {len(dead)} 个")
        lines.append("")
        lines.append("| 方式 | 表达式 | 说明 |")
        lines.append("|---|---|---|")
        for s in dead:
            note = "纯结构 xpath，无法静态判定" if s["hits"] is None else "静态语料中找不到"
            lines.append(f"| `{s['kind']}` | `{s['expr']}` | {note} |")
        lines.append("")

    lines.append("## 命中明细（可参考的现有实现位置）")
    lines.append("")
    for fname, d in per_file.items():
        alive = [s for s in d["selectors"] if s["hits"]]
        lines.append(f"### {fname} — {len(alive)}/{len(d['selectors'])} 命中")
        lines.append("")
        if d["pages"]:
            lines.append("- 引用页面：" + "、".join(f"`{p}`" for p in d["pages"]))
        if d["frames"]:
            lines.append("- iframe：" + "、".join(f"`{f}`" for f in d["frames"]))
        if alive:
            lines.append("")
            lines.append("| 方式 | 表达式 | 命中文件 |")
            lines.append("|---|---|---|")
            for s in alive:
                hs = ", ".join(f"`{h}`" for h in s["hits"][:4])
                if len(s["hits"]) > 4:
                    hs += f" …(+{len(s['hits']) - 4})"
                lines.append(f"| `{s['kind']}` | `{s['expr']}` | {hs} |")
        lines.append("")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / "selector-audit.md").write_text("\n".join(lines), encoding="utf-8")
    (OUT_DIR / "selector-audit.json").write_text(
        json.dumps(per_file, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    print(f"定位表达式合计: {total_sel}，其中静态失效: {dead_total}")
    print(f"输出: {OUT_DIR / 'selector-audit.md'}")


if __name__ == "__main__":
    main()
