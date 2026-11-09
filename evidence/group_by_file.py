import re, sys
from collections import defaultdict

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

by_file = defaultdict(list)
with open(r'evidence\localization_audit\all_id_literals.txt', 'r', encoding='utf-8') as f:
    for line in f:
        # e.g. [ID] AddTransactionSheet.kt:287 [Text] -> "Catat Transaksi"
        m = re.match(r'\[ID\]\s*([^:]+):(\d+)\s*\[([^\]]+)\]\s*->\s*(.*)', line.strip())
        if m:
            fname, line_no, kind, val = m.groups()
            by_file[fname].append((int(line_no), kind, val))

for fname, items in sorted(by_file.items()):
    print(f"=== {fname} ({len(items)} items) ===")
    for line_no, kind, val in items[:15]:
        print(f"  L{line_no} [{kind}]: {val}")
    if len(items) > 15:
        print(f"  ... and {len(items)-15} more")
