import os, re

components_dir = 'app/src/main/java/com/example/ui/components'

with open('evidence/localization_audit/components_scan_results.txt', 'w', encoding='utf-8') as out:
    for f in os.listdir(components_dir):
        if not f.endswith('.kt'):
            continue
        path = os.path.join(components_dir, f)
        with open(path, 'r', encoding='utf-8', errors='ignore') as fp:
            lines = fp.readlines()
        out.write(f"\n==========================================\n")
        out.write(f"COMPONENT: {f} (Total lines: {len(lines)})\n")
        out.write(f"==========================================\n")
        for i, line in enumerate(lines, 1):
            m = re.findall(r'Text\(\s*(?:text\s*=\s*)?"([^"]{3,})"', line)
            for s in m:
                if 'AppStrings' not in line and 'stringResource' not in line:
                    if not s.startswith('$') and not s.startswith('Rp') and not s.startswith('http'):
                        out.write(f"  Line {i:4d}: \"{s}\"\n")

print("Done scanning components.")
