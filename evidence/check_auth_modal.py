import re

with open('app/src/main/java/com/example/ui/components/AuthModalDialog.kt', 'r', encoding='utf-8', errors='ignore') as fp:
    lines = fp.readlines()

for i, line in enumerate(lines[180:400], 181):
    safe = line.strip().encode('ascii', 'replace').decode('ascii')
    if 'Text(' in line or 'label =' in line or 'placeholder =' in line:
        print(f"{i:4d}: {safe}")
