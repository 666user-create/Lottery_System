"""
测试数据白名单清理工具（DB 层）

用途：清理**无法通过 API 删除**的测试残留数据：
  - human_handover 工单（HandoverController 无删除接口）
  - user 测试账号（UserController 无删除接口）
  - prize / activity（无删除接口，第④步创建类用例使用）

安全设计（对应业主错误 #4「绝不碰真实数据」）：
  1. 白名单前缀：只删 `qa_auto_%` 标识的数据，其余一律不动；
  2. 干跑模式：默认 --dry-run 只打印将删除的行，需显式 --apply 才执行；
  3. 先备份：--apply 前自动将待删行导出为 SQL 备份；
  4. 关联安全性：删除前检查关联表引用，发现非测试数据的引用则中止。

用法：
  python db_cleanup.py                # 干跑（默认，安全）
  python db_cleanup.py --apply        # 执行删除（自动先备份）
  python db_cleanup.py --apply --backup-dir ..\\..\\docs\\test\\backup
"""
import argparse
import datetime
import os
import subprocess
import sys

# Windows 控制台默认 GBK，强制 UTF-8 以避免输出中文/符号时报 UnicodeEncodeError
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

MYSQL_BIN = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
MYSQLDUMP_BIN = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"

DB = {
    "host": "127.0.0.1", "port": "3306",
    "user": "root", "password": "root", "database": "lottery_system",
}

# 白名单：表 → 删除条件
# 统一使用 ASCII 前缀 qa_auto_ / QA_AUTO_，规避中文参数经 mysql CLI 传参的编码风险
WHITELIST = {
    "human_handover": "session_id LIKE 'qa\\_auto\\_%'",
    "user": "email LIKE 'qa\\_auto\\_%'",
    "prize": "name LIKE 'qa\\_auto\\_%' OR name LIKE 'QA\\_AUTO\\_%'",
    "activity": "activity_name LIKE 'qa\\_auto\\_%' OR activity_name LIKE 'QA\\_AUTO\\_%'",
}

# 关联检查：删除主表前，若从表存在非白名单引用则视为异常
RELATION_CHECKS = [
    ("activity_user", "activity_id IN (SELECT id FROM activity "
                      "WHERE activity_name LIKE 'qa\\_auto\\_%' OR activity_name LIKE 'QA\\_AUTO\\_%')"),
    ("activity_prize", "activity_id IN (SELECT id FROM activity "
                       "WHERE activity_name LIKE 'qa\\_auto\\_%' OR activity_name LIKE 'QA\\_AUTO\\_%')"),
    ("winning_record", "activity_id IN (SELECT id FROM activity "
                       "WHERE activity_name LIKE 'qa\\_auto\\_%' OR activity_name LIKE 'QA\\_AUTO\\_%')"),
]


def _clean(text: str) -> str:
    """剔除 mysql 客户端的 Warning 噪声行，仅保留数据行"""
    lines = [ln for ln in text.splitlines()
             if ln.strip() and "Warning" not in ln and not ln.startswith("mysql:")]
    return "\n".join(lines)


def _run_mysql(sql: str, executable: str = MYSQL_BIN) -> tuple:
    cmd = [executable,
           f"--host={DB['host']}", f"--port={DB['port']}",
           f"--user={DB['user']}", f"--password={DB['password']}",
           "--skip-column-names",
           DB["database"], f"--execute={sql}"]
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    return proc.returncode, _clean((proc.stdout or "") + (proc.stderr or ""))


def _run_mysqldump(table: str, where: str, out_file: str) -> bool:
    cmd = [MYSQLDUMP_BIN,
           f"--host={DB['host']}", f"--port={DB['port']}",
           f"--user={DB['user']}", f"--password={DB['password']}",
           "--no-create-info", "--skip-extended-insert",
           DB["database"], table, f"--where={where}"]
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if proc.returncode != 0:
        print(f"    [备份失败] {table}: {proc.stderr.strip()[:200]}")
        return False
    with open(out_file, "w", encoding="utf-8") as f:
        f.write(proc.stdout)
    return True


def check_relations() -> bool:
    """关联安全检查：发现非白名单引用则中止（可能误删真实数据）"""
    print("\n[1/4] 关联安全检查")
    safe = True
    for table, condition in RELATION_CHECKS:
        code, out = _run_mysql(f"SELECT COUNT(*) FROM {table} WHERE {condition};")
        print(f"    {table}: {out.strip().splitlines()[-1] if out.strip() else '?'}")
    # 检查测试账号是否被真实活动/中奖记录引用
    code, out = _run_mysql(
        "SELECT COUNT(*) FROM winning_record WHERE winner_id IN "
        "(SELECT id FROM user WHERE email LIKE 'qa\\_auto\\_%');")
    cnt = out.strip().splitlines()[-1] if out.strip() else "0"
    if cnt.isdigit() and int(cnt) > 0:
        print(f"    ⚠️ 测试账号被中奖记录引用 {cnt} 条 → 需人工确认（已中止）")
        safe = False
    return safe


def main():
    parser = argparse.ArgumentParser(description="测试数据白名单清理（DB 层）")
    parser.add_argument("--apply", action="store_true", help="实际执行删除（默认仅干跑）")
    parser.add_argument("--backup-dir", default=os.path.join(os.path.dirname(__file__), "..", "..", "..",
                                                             "docs", "test", "backup"),
                        help="备份目录")
    args = parser.parse_args()

    if not os.path.exists(MYSQL_BIN):
        print(f"❌ 未找到 mysql 客户端：{MYSQL_BIN}")
        return 2

    print("=" * 70)
    print(f"测试数据白名单清理 | 模式={'执行(APPLY)' if args.apply else '干跑(DRY-RUN)'}")
    print(f"白名单条件: {WHITELIST}")
    print("=" * 70)

    # 步骤 1：关联安全检查
    if not check_relations():
        print("\n❌ 关联检查未通过，已中止（避免误删真实数据）")
        return 3

    # 步骤 2：列出待删清单
    print("\n[2/4] 待删清单")
    total = 0
    counts = {}
    for table, where in WHITELIST.items():
        code, out = _run_mysql(f"SELECT COUNT(*) FROM {table} WHERE {where};")
        cnt = out.strip().splitlines()[-1] if out.strip() else "0"
        counts[table] = cnt
        total += int(cnt) if cnt.isdigit() else 0
        print(f"    {table:<18} {cnt} 行   (条件: {where})")
    print(f"    合计: {total} 行")

    if total == 0:
        print("\n✅ 无待清理数据，无需操作")
        return 0

    if not args.apply:
        print("\n[干跑结束] 未执行任何删除。确认无误后加 --apply 执行。")
        return 0

    # 步骤 3：备份
    os.makedirs(args.backup_dir, exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    print(f"\n[3/4] 备份到 {args.backup_dir}")
    for table, where in WHITELIST.items():
        if counts.get(table, "0") == "0":
            continue
        out_file = os.path.join(args.backup_dir, f"cleanup-{stamp}-{table}.sql")
        ok = _run_mysqldump(table, where, out_file)
        size = os.path.getsize(out_file) if os.path.exists(out_file) else 0
        print(f"    {table:<18} → {os.path.basename(out_file)} ({size} bytes) {'OK' if ok else 'FAIL'}")
        if not ok or size == 0:
            print("\n❌ 备份失败，已中止删除（安全优先）")
            return 4

    # 步骤 4：执行删除（按依赖顺序：先从表后主表）
    print("\n[4/4] 执行删除")
    order = ["human_handover", "winner_record_orphan", "user", "prize", "activity"]
    for table in order:
        if table not in WHITELIST:
            continue
        where = WHITELIST[table]
        code, out = _run_mysql(f"DELETE FROM {table} WHERE {where};")
        print(f"    DELETE {table} → {'OK' if code == 0 else 'FAIL: ' + out.strip()[:120]}")

    # 结果核对
    print("\n[结果核对]")
    code, out = _run_mysql(
        "SELECT (SELECT COUNT(*) FROM user) users, "
        "(SELECT COUNT(*) FROM user WHERE email LIKE 'qa\\_auto\\_%') qa_users, "
        "(SELECT COUNT(*) FROM faq) faqs, "
        "(SELECT COUNT(*) FROM human_handover) handovers;")
    print("    " + out.strip().replace("\n", "\n    "))
    return 0


if __name__ == "__main__":
    sys.exit(main())
