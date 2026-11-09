$adb = "E:\AndroidStudioSDK\platform-tools\adb.exe"

function Tap-Text {
    param([string]$pattern)
    & $adb -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml | Out-Null
    $xml = & $adb -s emulator-5554 shell cat /sdcard/window_dump.xml
    $match = [regex]::Match($xml, "text=""($pattern)""[^>]+bounds=""\[(\d+),(\d+)\]\[(\d+),(\d+)\]""")
    if ($match.Success) {
        $x1 = [int]$match.Groups[2].Value
        $y1 = [int]$match.Groups[3].Value
        $x2 = [int]$match.Groups[4].Value
        $y2 = [int]$match.Groups[5].Value
        $x = [int](($x1 + $x2) / 2)
        $y = [int](($y1 + $y2) / 2)
        Write-Output "Tapping '$($match.Groups[1].Value)' at ($x, $y)"
        & $adb -s emulator-5554 shell input tap $x $y
        return $true
    } else {
        Write-Output "Pattern '$pattern' not found in UI dump"
        return $false
    }
}

# 1. Tap Pengaturan tab in bottom nav
Tap-Text "Pengaturan"
Start-Sleep -Seconds 1

# 2. Tap Ganti Akun / Masuk
Tap-Text "Ganti Akun / Masuk"
Start-Sleep -Seconds 1

# 3. Tap Email field
Tap-Text "Email atau Username"
Start-Sleep -Milliseconds 600

# 4. Type Lukas
& $adb -s emulator-5554 shell input text "Lukas"
Start-Sleep -Milliseconds 600

# 5. Tap Masuk Sekarang
Tap-Text "Masuk Sekarang"
Start-Sleep -Seconds 2

# 6. Check Buat Kata Sandi dialog
Tap-Text "Kata Sandi Baru"
Start-Sleep -Milliseconds 600
& $adb -s emulator-5554 shell input text "Lukas2026"
Start-Sleep -Milliseconds 600

# Tap Konfirmasi Kata Sandi
Tap-Text "Konfirmasi Kata Sandi"
Start-Sleep -Milliseconds 600
& $adb -s emulator-5554 shell input text "Lukas2026"
Start-Sleep -Milliseconds 600

# Hide keyboard
& $adb -s emulator-5554 shell input keyevent 4
Start-Sleep -Milliseconds 800

# Tap Simpan & Masuk
Tap-Text "Simpan &amp; Masuk"
Start-Sleep -Seconds 2

& $adb -s emulator-5554 shell screencap -p /sdcard/16_master_dev_profile.png
& $adb -s emulator-5554 pull /sdcard/16_master_dev_profile.png E:\FinSmart\qa-recordings\run-20261012-051100\screenshots\16_master_dev_profile.png
