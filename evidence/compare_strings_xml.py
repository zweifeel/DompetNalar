import xml.etree.ElementTree as ET, os

for d in ['values', 'values-en', 'values-in']:
    xml_path = os.path.join('app/src/main/res', d, 'strings.xml')
    tree = ET.parse(xml_path)
    root = tree.getroot()
    print(f"=== {d}/strings.xml ===")
    for s in root.findall('string')[:15]:
        print(f"  {s.attrib.get('name')}: {s.text}")
