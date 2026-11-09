import os

with open(r'evidence\localization_audit\targeted_ui_raw_strings.txt', 'r', encoding='utf-8') as f:
    lines = [line.strip() for line in f if line.startswith('[ID]')]

with open(r'evidence\localization_audit\all_id_literals.txt', 'w', encoding='utf-8') as out:
    for l in lines:
        out.write(l + '\n')

print(f"Total lines written: {len(lines)}")
