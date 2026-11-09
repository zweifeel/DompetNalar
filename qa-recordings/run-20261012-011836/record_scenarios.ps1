# Script Perekaman Skenario FinSmart QA
$adb = "E:\AndroidStudioSDK\platform-tools\adb.exe"
$qaDir = "E:\FinSmart\qa-recordings\run-20261012-011836"
$rawDir = "$qaDir\raw"
$screenDir = "$qaDir\screenshots"

function Record-Clip {
    param(
        [string]$Name,
        [int]$Duration,
        [scriptblock]$Actions
    )
    Write-Host "========================================="
    Write-Host "Merekam Skenario: $Name (Durasi max: $Duration s)"
    Write-Host "========================================="

    # Pastikan file lama di sdcard dibersihkan
    & $adb -s emulator-5554 shell rm -f "/sdcard/$Name.mp4"

    # Jalankan screenrecord di background process
    $proc = Start-Process -FilePath $adb -ArgumentList "-s emulator-5554 shell screenrecord --time-limit $Duration /sdcard/$Name.mp4" -PassThru
    Start-Sleep -Seconds 2

    # Jalankan aksi
    & $Actions

    # Tunggu screenrecord selesai
    Write-Host "Menunggu perekaman selesai..."
    $proc.WaitForExit()
    Start-Sleep -Seconds 1

    # Tarik file video
    $destVideo = "$rawDir\$Name.mp4"
    & $adb -s emulator-5554 pull "/sdcard/$Name.mp4" $destVideo

    # Ambil screenshot kondisi akhir
    $destPng = "$screenDir\$Name.png"
    & $adb -s emulator-5554 shell screencap -p "/sdcard/$Name.png"
    & $adb -s emulator-5554 pull "/sdcard/$Name.png" $destPng

    if (Test-Path $destVideo) {
        $size = (Get-Item $destVideo).Length
        Write-Host "BERHASIL: $Name.mp4 tersimpan ($size bytes)" -ForegroundColor Green
    } else {
        Write-Host "GAGAL: $Name.mp4 tidak ditemukan!" -ForegroundColor Red
    }
}

# SKENARIO 1: Startup & Dashboard
Record-Clip -Name "01_startup_dashboard" -Duration 12 -Actions {
    & $adb -s emulator-5554 shell input tap 90 2330
    Start-Sleep -Seconds 1
    # Scroll ke bawah melihat dompet & quick actions
    & $adb -s emulator-5554 shell input swipe 540 1600 540 1000 300
    Start-Sleep -Seconds 2
    # Scroll kembali ke atas
    & $adb -s emulator-5554 shell input swipe 540 1000 540 1600 300
    Start-Sleep -Seconds 1
    # Sensor Saldo (Incognito)
    & $adb -s emulator-5554 shell input tap 865 390
    Start-Sleep -Seconds 2
    & $adb -s emulator-5554 shell input tap 865 390
    Start-Sleep -Seconds 1
}

# SKENARIO 2: Transaksi Pemasukan Demo (Rp 8.000.000)
Record-Clip -Name "02_transaksi_pemasukan" -Duration 14 -Actions {
    # Tap tombol '+ Pemasukan' di Dashboard (x=180, y=1750)
    & $adb -s emulator-5554 shell input tap 180 1750
    Start-Sleep -Seconds 2
    # Tap field Nominal Transaksi (x=300, y=280)
    & $adb -s emulator-5554 shell input tap 300 280
    Start-Sleep -Milliseconds 500
    & $adb -s emulator-5554 shell input text "8000000"
    Start-Sleep -Milliseconds 500
    # Tutup keyboard
    & $adb -s emulator-5554 shell input keyevent 111
    Start-Sleep -Milliseconds 500
    # Pilih Kategori 'Gaji Pokok' (x=580, y=580)
    & $adb -s emulator-5554 shell input tap 580 580
    Start-Sleep -Milliseconds 500
    # Tap Judul / Deskripsi (x=350, y=750)
    & $adb -s emulator-5554 shell input tap 350 750
    Start-Sleep -Milliseconds 500
    & $adb -s emulator-5554 shell input text "Gaji%sBulanan"
    Start-Sleep -Milliseconds 500
    & $adb -s emulator-5554 shell input keyevent 111
    Start-Sleep -Milliseconds 500
    # Tap tombol hijau 'Simpan Transaksi' (x=540, y=930)
    & $adb -s emulator-5554 shell input tap 540 930
    Start-Sleep -Seconds 3
}

# SKENARIO 3: Transaksi Pengeluaran Demo (Rp 1.250.000)
Record-Clip -Name "03_transaksi_pengeluaran" -Duration 14 -Actions {
    # Tap tombol '- Pengeluaran' di Dashboard (x=500, y=1750)
    & $adb -s emulator-5554 shell input tap 500 1750
    Start-Sleep -Seconds 2
    # Tap field Nominal Transaksi
    & $adb -s emulator-5554 shell input tap 300 280
    Start-Sleep -Milliseconds 500
    & $adb -s emulator-5554 shell input text "1250000"
    Start-Sleep -Milliseconds 500
    & $adb -s emulator-5554 shell input keyevent 111
    Start-Sleep -Milliseconds 500
    # Pilih Kategori pertama (Makanan & Minuman ~ x=230, y=580)
    & $adb -s emulator-5554 shell input tap 230 580
    Start-Sleep -Milliseconds 500
    # Tap Judul
    & $adb -s emulator-5554 shell input tap 350 750
    Start-Sleep -Milliseconds 500
    & $adb -s emulator-5554 shell input text "Belanja%sBulanan"
    Start-Sleep -Milliseconds 500
    & $adb -s emulator-5554 shell input keyevent 111
    Start-Sleep -Milliseconds 500
    # Simpan Transaksi
    & $adb -s emulator-5554 shell input tap 540 930
    Start-Sleep -Seconds 3
}

# SKENARIO 4: Riwayat Transaksi (Mutasi) & Banner AdMob
Record-Clip -Name "04_mutasi_banner" -Duration 12 -Actions {
    # Tap tab Mutasi (x=450, y=2330)
    & $adb -s emulator-5554 shell input tap 450 2330
    Start-Sleep -Seconds 3
    # Scroll sedikit untuk melihat rekap dan banner
    & $adb -s emulator-5554 shell input swipe 540 1500 540 1200 300
    Start-Sleep -Seconds 2
    # Tap tab Bulanan (x=540, y=240)
    & $adb -s emulator-5554 shell input tap 540 240
    Start-Sleep -Seconds 2
}

# SKENARIO 5: Analitik & AI Health Score & Banner AdMob
Record-Clip -Name "05_analisis_banner" -Duration 12 -Actions {
    # Tap tab Analisis (x=810, y=2330)
    & $adb -s emulator-5554 shell input tap 810 2330
    Start-Sleep -Seconds 3
    # Scroll ke bawah melihat grafik proporsi pengeluaran
    & $adb -s emulator-5554 shell input swipe 540 1600 540 1000 300
    Start-Sleep -Seconds 2
    # Scroll kembali
    & $adb -s emulator-5554 shell input swipe 540 1000 540 1600 300
    Start-Sleep -Seconds 2
}

# SKENARIO 6: Paywall Upgrade & Tombol Rewarded Ad 1 Jam
Record-Clip -Name "06_paywall_rewarded" -Duration 14 -Actions {
    # Tap tab Pengaturan (x=990, y=2330)
    & $adb -s emulator-5554 shell input tap 990 2330
    Start-Sleep -Seconds 2
    # Tap 'Upgrade ke Smart Master' (x=540, y=370)
    & $adb -s emulator-5554 shell input tap 540 370
    Start-Sleep -Seconds 3
    # Scroll ke bawah paywall melihat tombol Rewarded Ad 1 Jam
    & $adb -s emulator-5554 shell input swipe 540 1800 540 800 300
    Start-Sleep -Seconds 3
    # Tekan back untuk menutup paywall
    & $adb -s emulator-5554 shell input keyevent 4
    Start-Sleep -Seconds 1
}

# SKENARIO 7: Gamifikasi Hub & Hadiah Streak 7 Hari (5 Jam) & 30 Hari (24 Jam)
Record-Clip -Name "07_gamifikasi_streak" -Duration 14 -Actions {
    # Di Pengaturan, scroll ke bawah ke opsi Gamifikasi
    & $adb -s emulator-5554 shell input swipe 540 1800 540 800 300
    Start-Sleep -Seconds 2
    # Tap 'Gamifikasi, Daily Streak & Badges' (x=540, y=880)
    & $adb -s emulator-5554 shell input tap 540 880
    Start-Sleep -Seconds 4
    # Tutup dialog Gamifikasi Hub
    & $adb -s emulator-5554 shell input keyevent 4
    Start-Sleep -Seconds 1
    # Kembali ke Dasbor utama
    & $adb -s emulator-5554 shell input tap 90 2330
    Start-Sleep -Seconds 1
}

Write-Host "SELURUH PEREKAMAN SKENARIO SELESAI!" -ForegroundColor Cyan
