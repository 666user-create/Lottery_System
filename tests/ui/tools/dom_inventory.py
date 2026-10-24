# -*- coding: utf-8 -*-
"""
UI 自动化重构 · 页面 DOM 清单提取工具
=====================================

用途
----
UI 大幅改版后，Selenium 用例里**所有选择器都必须重新从当前 HTML 推导**，
不能再沿用旧版假设。本工具把 static 下每个页面的可定位信息抽取成一份
紧凑清单，供人（和自动化）挑选稳定选择器。

为什么要有它
------------
- 21 个页面 HTML 合计约 7000 行，逐个人工翻看既慢又容易漏；
- 选择器稳定性需要「同一元素是否有稳定 id/name」这类统计判断，
  抽取后一眼可比；
- 清单文件可以随代码一起留着，下次改版做 diff 就能看出「哪些选择器被打断了」。

抽取内容
--------
1. `<title>`：页面身份
2. 所有 `id="..."`：**首选选择器**（最稳定）
3. 所有 `name="..."`：表单首选（登录/注册用例大量依赖）
4. `data-*` 属性名：新版 UI 常用它做行为标记
5. `class` 词表：兜底选择器（改动最频繁，只在前两者缺失时用）
6. 引用的 JS 文件：判断页面挂了哪些脚本（auth-guard / api-base 等）
7. 页面内出现的接口路径（`/xxx/yyy` 形态）：**顺带核对页面调了哪些接口**
8. 按钮/链接可见文本：alert 与跳转类断言常用

运行
----
    cd D:\\java\\lottery_system\\tests\\ui\\tools
    $env:PYTHONIOENCODING="utf-8"; python dom_inventory.py

输出
----
    tests/ui/tools/out/dom-inventory.md   —— 人读清单
    tests/ui/tools/out/dom-inventory.json —— 机读清单（后续脚本可直接用）
"""

import json
import re
from pathlib import Path

# static 目录：本脚本位于 tests/ui/tools/，往上 4 级到仓库根
ROOT = Path(__file__).resolve().parents[3]
STATIC = ROOT / "src" / "main" / "resources" / "static"
OUT = Path(__file__).resolve().parent / "out"


def uniq(seq):
    """去重且保持出现顺序 —— 顺序本身有信息量（越靠前越可能在使用）。"""
    seen, out = set(), []
    for x in seq:
        if x not in seen:
            seen.add(x)
            out.append(x)
    return out


def extract(html: str) -> dict:
    """从单个页面 HTML 抽取定位信息。"""
    # id：只取标签上的 id="xxx"，避开 JS 字符串里的 getElementById('xxx')
    ids = uniq(re.findall(r'<[a-zA-Z][^>]*\bid="([^"]+)"', html))
    names = uniq(re.findall(r'<[a-zA-Z][^>]*\bname="([^"]+)"', html))
    datas = uniq(re.findall(r'\b(data-[a-z0-9-]+)=', html))

    # class：拆成单词，过滤纯样式类（bootstrap 工具类等）以减少噪声
    noise = re.compile(
        r'^(container|row|col|text-|btn-|form-|table-|card-|mb-|mt-|p[xytrbl]?-|m[xytrbl]?-|'
        r'align-|justify-|d-|w-|h-|g-|fs-|fw-|lh-|border|rounded|shadow|bg-|badge|nav-|'
        r'active|disabled|show|hide|fade|collapse|modal-|dropdown-|input-group|list-|'
        r'visually|sr-only|small|lead|small-|position-|overflow-|user-select|flex-|'
        r'gap-|order-|start-|end-|top-|bottom-|float-|clearfix|d-none|d-flex|d-block)')
    classes = []
    for attr in re.findall(r'\bclass="([^"]*)"', html):
        for c in attr.split():
            if len(c) > 2 and not noise.match(c):
                classes.append(c)
    classes = uniq(classes)

    scripts = uniq(re.findall(r'<script[^>]*\bsrc="([^"]+)"', html))

    # 接口路径：'...' 或 "..." 中以 / 开头、含字母的短串
    apis = uniq([p for p in re.findall(r'["\'](/[a-zA-Z][a-zA-Z0-9/_\-{}.]*)["\']', html)
                 if not p.endswith(('.html', '.css', '.js', '.png', '.jpg', '.ico'))])

    # 按钮与链接文本：断言 alert/跳转时用得上
    labels = uniq([t.strip() for t in re.findall(r'<(?:button|a)[^>]*>([^<>{}]{2,40})</(?:button|a)>', html)
                   if t.strip()])

    title = re.search(r'<title>([^<]*)</title>', html)
    return {
        "title": title.group(1).strip() if title else "",
        "ids": ids,
        "names": names,
        "data": datas,
        "classes": classes,
        "scripts": scripts,
        "apis": apis,
        "labels": labels,
        "lines": html.count("\n") + 1,
    }


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    pages = sorted(STATIC.glob("*.html"))
    result = {}
    for p in pages:
        html = p.read_text(encoding="utf-8", errors="replace")
        result[p.name] = extract(html)

    (OUT / "dom-inventory.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    md = ["# UI 页面 DOM 清单（改版后重新提取）", "",
          f"- 提取时间基准：本次重构勘察", 
          f"- 页面数：**{len(pages)}**（`src/main/resources/static/*.html`）",
          "- 选择器优先级建议：`id` > `name` > `data-*` > 稳定 class；下表 class 已过滤纯样式类", ""]
    md.append("| 页面 | title | 行数 | id | name | data-* | class(过滤后) |")
    md.append("|---|---|---|---:|---:|---:|---:|")
    for name, d in result.items():
        md.append("| `{n}` | {t} | {l} | {i} | {nm} | {da} | {c} |".format(
            n=name, t=d["title"], l=d["lines"], i=len(d["ids"]),
            nm=len(d["names"]), da=len(d["data"]), c=len(d["classes"])))

    for name, d in result.items():
        md += ["", f"## {name}", f"- title: `{d['title']}`", f"- 行数: {d['lines']}"]
        for key, label in (("ids", "id"), ("names", "name"), ("data", "data-*"),
                           ("scripts", "脚本"), ("labels", "可见文本")):
            if d[key]:
                md.append(f"- **{label}**({len(d[key])}): " + ", ".join(f"`{x}`" for x in d[key]))
        if d["classes"]:
            md.append(f"- class({len(d['classes'])}): " + ", ".join(f"`{x}`" for x in d["classes"][:40])
                      + (" …" if len(d["classes"]) > 40 else ""))
        if d["apis"]:
            md.append(f"- 调用接口({len(d['apis'])}): " + ", ".join(f"`{x}`" for x in d["apis"]))

    (OUT / "dom-inventory.md").write_text("\n".join(md) + "\n", encoding="utf-8")

    print(f"页面数: {len(pages)}")
    print(f"输出: {OUT / 'dom-inventory.md'}")
    print(f"输出: {OUT / 'dom-inventory.json'}")
    total_ids = sum(len(d["ids"]) for d in result.values())
    total_names = sum(len(d["names"]) for d in result.values())
    print(f"合计 id={total_ids}  name={total_names}")
    no_id = [n for n, d in result.items() if not d["ids"]]
    if no_id:
        print("无 id 的页面: " + ", ".join(no_id))


if __name__ == "__main__":
    main()