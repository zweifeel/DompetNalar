import subprocess, time, xml.etree.ElementTree as ET, re, os

ADB = r'E:\AndroidStudioSDK\platform-tools\adb.exe'
SERIAL = 'PZGMWGKZAA5XJZTW'
PACKAGE = 'com.aistudio.finsmart.kptxvw.debug'

def adb_shell(cmd_list):
    res = subprocess.run([ADB, '-s', SERIAL, 'shell'] + cmd_list, capture_output=True, text=True, errors='replace')
    return res.stdout

def take_screenshot(filepath):
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    with open(filepath, 'wb') as f:
        subprocess.run([ADB, '-s', SERIAL, 'exec-out', 'screencap', '-p'], stdout=f, check=True)
    print(f"Screenshot captured: {filepath} ({os.path.getsize(filepath)} bytes)")

def dump_ui():
    adb_shell(['uiautomator', 'dump', '/sdcard/dump.xml'])
    xml_str = adb_shell(['cat', '/sdcard/dump.xml'])
    try:
        return ET.fromstring(xml_str)
    except Exception as e:
        print(f"Error parsing dump.xml: {e}")
        return None

def find_node_by_text(root, text_regex):
    if root is None:
        return None
    for elem in root.iter():
        text = elem.attrib.get('text', '')
        if re.search(text_regex, text, re.I):
            return elem
    return None

def click_node(node):
    if node is None:
        return False
    bounds = node.attrib.get('bounds', '')
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if m:
        x = (int(m.group(1)) + int(m.group(3))) // 2
        y = (int(m.group(2)) + int(m.group(4))) // 2
        print(f"Clicking node '{node.attrib.get('text')}' at ({x}, {y})")
        adb_shell(['input', 'tap', str(x), str(y)])
        time.sleep(1)
        return True
    return False

def click_text(text_regex):
    root = dump_ui()
    node = find_node_by_text(root, text_regex)
    if node:
        return click_node(node)
    else:
        print(f"Text matching '{text_regex}' not found on screen.")
        return False

print("Device navigation helper ready.")
