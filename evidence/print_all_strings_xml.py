import xml.etree.ElementTree as ET, os

en_tree = ET.parse('app/src/main/res/values-en/strings.xml')
in_tree = ET.parse('app/src/main/res/values-in/strings.xml')

en_map = {s.attrib.get('name'): s.text for s in en_tree.getroot().findall('string')}
in_map = {s.attrib.get('name'): s.text for s in in_tree.getroot().findall('string')}

print("=== ALL 47 STRINGS COMPARISON ===")
for k in en_map:
    print(f"Key: {k}")
    print(f"  EN: {en_map.get(k)}")
    print(f"  ID: {in_map.get(k)}")
