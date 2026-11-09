import os

for root, dirs, files in os.walk('app/src/main/java'):
    for f in files:
        if f.endswith('.kt'):
            p = os.path.join(root, f)
            with open(p, 'r', encoding='utf-8') as fp:
                txt = fp.read()
            if '\\"' in txt:
                print('Found in:', p)
                # Fix inside string templates: e.g. ${...\"...\"...} -> replace with "
                # Specifically replace com.example.util.AppStrings.t(\" -> com.example.util.AppStrings.t("
                new_txt = txt.replace('com.example.util.AppStrings.t(\\"', 'com.example.util.AppStrings.t("')
                new_txt = new_txt.replace('AppStrings.t(\\"', 'AppStrings.t("')
                new_txt = new_txt.replace('\\", \\"', '", "')
                new_txt = new_txt.replace('\\")', '")')
                new_txt = new_txt.replace('\\"W\\"', '"W"')
                new_txt = new_txt.replace('\\"M\\"', '"M"')
                if new_txt != txt:
                    with open(p, 'w', encoding='utf-8') as fp:
                        fp.write(new_txt)
                    print('Fixed:', p)
