with open('app/build/reports/tests/testDebugUnitTest/index.html', 'r', encoding='utf-8') as f:
    text = f.read()

import re
for m in re.finditer(r'<div class=["\']counter["\']>(\d+)</div>\s*<p>([^<]+)</p>', text):
    print(f'{m.group(2)}: {m.group(1)}')
for m in re.finditer(r'<div class=["\']percent["\']>([^<]+)</div>\s*<p>([^<]+)</p>', text):
    print(f'{m.group(2)}: {m.group(1)}')
