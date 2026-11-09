import os
import re

base_dir = r'app\src\main\java'
matches = []

for root, dirs, files in os.walk(base_dir):
    for f in files:
        if f.endswith('.kt'):
            p = os.path.join(root, f)
            with open(p, 'r', encoding='utf-8', errors='replace') as fp:
                for idx, line in enumerate(fp, 1):
                    if re.search(r'Locale\s*\(\s*["\'](id|in)["\']', line):
                        rel = os.path.relpath(p, base_dir)
                        matches.append((rel, idx, line.strip()))

print(f"Total hardcoded ID locale occurrences: {len(matches)}")
for rel, idx, l in matches:
    print(f"{rel}:{idx} -> {l}")
