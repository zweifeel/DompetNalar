import subprocess, xml.etree.ElementTree as ET

adb = r'E:\AndroidStudioSDK\platform-tools\adb.exe'
serial = 'PZGMWGKZAA5XJZTW'

subprocess.run([adb, '-s', serial, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'], check=True)
res = subprocess.run([adb, '-s', serial, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True, errors='replace')
root = ET.fromstring(res.stdout)

print("=== NODES ON FOREGROUND SCREEN ===")
for elem in root.iter():
    t = elem.attrib.get('text', '')
    if t:
        safe_t = t.encode('ascii', 'replace').decode('ascii')
        print(f"  {safe_t} -> {elem.attrib.get('bounds')}")
