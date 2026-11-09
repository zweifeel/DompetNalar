package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.AppConfig
import com.example.ui.theme.FinSmartPrimary
import com.example.util.LocaleHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProminentDisclosureDialog(
    isFirstRun: Boolean = false,
    onAcknowledge: () -> Unit = {},
    onDeclineAndExit: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEnglish = LocaleHelper.isEnglish()

    ModalBottomSheet(
        onDismissRequest = {
            if (isFirstRun) {
                onDeclineAndExit()
            } else {
                onDismiss()
            }
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("prominent_disclosure_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(FinSmartPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = FinSmartPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Privacy Policy & Transparency" else "Kebijakan Privasi & Transparansi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEnglish) "Data Usage Transparency & Device Permissions" else "Transparansi Penggunaan Data & Akses Izin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isFirstRun) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = if (isEnglish) "Close" else "Tutup"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Disclosure Statement
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (isEnglish) "🔒 ${AppConfig.APP_NAME} Privacy Commitment" else "🔒 Komitmen Privasi ${AppConfig.APP_NAME}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEnglish) {
                            "${AppConfig.APP_NAME} is developed by ${AppConfig.APP_AUTHOR}. All your financial transactions and wallet balances are stored locally and privately on your device's database (Room SQLite) by default. ${AppConfig.APP_NAME} does not sell your personal data. Cloud interactions are strictly user-directed: Google Drive is used exclusively for encrypted backup archives (AES-256-GCM) upon your explicit command, on-device AI processes receipt images locally during scanning, and user feedback is submitted securely to our Firestore collection without sensitive financial history."
                        } else {
                            "${AppConfig.APP_NAME} dikembangkan oleh ${AppConfig.APP_AUTHOR}. Seluruh catatan keuangan dan transaksi Anda secara bawaan tersimpan secara lokal dan privat pada basis data perangkat Anda (Room SQLite). ${AppConfig.APP_NAME} tidak menjual data pribadi Anda. Akses cloud bersifat opsional: Google Drive hanya digunakan untuk mencadangkan data terenkripsi (AES-256-GCM) atas instruksi Anda, pemrosesan OCR dilakukan secara lokal di perangkat Anda saat memindai nota, dan masukan pengguna dikirimkan secara aman ke server Firestore resmi tanpa menyertakan riwayat keuangan."
                        },
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isEnglish) "Device Permission Disclosures:" else "Penggunaan Izin Sensitif (Prominent Disclosures):",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Disclosures Items
            DisclosureItem(
                icon = Icons.Default.Notifications,
                title = if (isEnglish) "1. Reminder Notifications" else "1. Notifikasi Pengingat (Reminders & Alerts)",
                description = if (isEnglish) {
                    "Used to display local transaction due-date alerts, scheduled recurring payment reminders, and budget threshold warnings entirely on your device."
                } else {
                    "Digunakan untuk menampilkan pemberitahuan pengingat jatuh tempo transaksi, jadwal pembayaran rutin, dan peringatan batas anggaran secara lokal di perangkat Anda."
                }
            )

            DisclosureItem(
                icon = Icons.Default.Mic,
                title = if (isEnglish) "2. Microphone Access (Audio Record)" else "2. Mikrofon (Audio Record)",
                description = if (isEnglish) {
                    "Used ONLY when you tap the voice button for Voice-to-Text Expense Logging, converting spoken commands into quick transaction records via system speech recognition."
                } else {
                    "Digunakan HANYA saat pengguna menekan tombol mikrofon untuk fitur Voice-to-Text Expense Logging, mengubah ucapan menjadi pencatatan transaksi cepat melalui antarmuka pengenalan suara sistem."
                }
            )

            DisclosureItem(
                icon = Icons.Default.CameraAlt,
                title = if (isEnglish) "3. Camera Access" else "3. Kamera (Camera Access)",
                description = if (isEnglish) {
                    "Used ONLY when you capture a physical shopping receipt or bill photo for automated on-device OCR extraction."
                } else {
                    "Digunakan HANYA saat pengguna mengambil foto nota/struk belanja fisik untuk diekstrak secara otomatis oleh mesin OCR lokal di perangkat Anda."
                }
            )

            DisclosureItem(
                icon = Icons.Default.CloudSync,
                title = if (isEnglish) "4. Google Drive Sync & Backup" else "4. Google Drive Sync & Backup",
                description = if (isEnglish) {
                    "Used ONLY to securely upload and download encrypted backup archives (AES-256-GCM) to your private Google Drive 'DompetNalar_Backups' folder upon your command."
                } else {
                    "Digunakan HANYA untuk menyimpan dan mengunduh berkas cadangan data terenkripsi (AES-256-GCM) milik pengguna di folder 'DompetNalar_Backups' pada akun Google Drive pribadi pengguna."
                }
            )

            DisclosureItem(
                icon = Icons.Default.Policy,
                title = if (isEnglish) "5. User Rights & Account Deletion" else "5. Hak Pengguna & Hapus Akun",
                description = if (isEnglish) {
                    "You have the absolute right to wipe your entire account and transaction history permanently at any time via 'Permanently Delete Account & Data' in Settings."
                } else {
                    "Pengguna berhak menghapus seluruh akun dan data riwayat transaksi secara permanen kapan saja melalui menu 'Hapus Akun & Data Permanen' di menu Pengaturan."
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Developer Contact Info (Section D)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isEnglish) "Developer Information:" else "Informasi Pengembang:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${if (isEnglish) "Application" else "Aplikasi"}: ${AppConfig.APP_NAME}\n" +
                                "${if (isEnglish) "Developer / Brand" else "Pengembang / Brand"}: ${AppConfig.APP_AUTHOR}\n" +
                                "${if (isEnglish) "Official Support" else "Email Dukungan"}: ${AppConfig.SUPPORT_EMAIL}\n" +
                                "${if (isEnglish) "Version" else "Versi"}: ${AppConfig.APP_VERSION_NAME} (Build ${AppConfig.APP_VERSION_CODE})",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            if (isFirstRun) {
                Button(
                    onClick = onAcknowledge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_acknowledge_disclosure"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (isEnglish) "I Have Read & Understood" else "Saya Telah Membaca & Memahami",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDeclineAndExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_decline_disclosure"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = if (isEnglish) "Decline & Exit Application" else "Tolak & Keluar Aplikasi",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_close_disclosure"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Close" else "Tutup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DisclosureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
