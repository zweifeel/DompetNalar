import subprocess, os, xml.etree.ElementTree as ET

adb = r'E:\AndroidStudioSDK\platform-tools\adb.exe'
serial = 'PZGMWGKZAA5XJZTW'

res = subprocess.run([adb, '-s', serial, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True, errors='replace')
root = ET.fromstring(res.stdout)

for elem in root.iter():
    b = elem.attrib.get('bounds', '')
    if any(y in b for y in ['1320', '1330', '1340', '1350', '1360', '1370', '1380', '1390', '1400', '1408', '1410', '1420', '1430']):
        print(f'{elem.tag} | class={elem.attrib.get("class")} | text="{elem.attrib.get("text")}" | bounds={b}')
