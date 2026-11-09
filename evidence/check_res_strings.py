import xml.etree.ElementTree as ET, os

res_dir = 'app/src/main/res'
dirs = [d for d in os.listdir(res_dir) if d.startswith('values')]

print("Resource directories found:", dirs)

for d in dirs:
    xml_path = os.path.join(res_dir, d, 'strings.xml')
    if os.path.exists(xml_path):
        tree = ET.parse(xml_path)
        root = tree.getroot()
        strings = root.findall('string')
        print(f"{d}/strings.xml contains {len(strings)} strings")
