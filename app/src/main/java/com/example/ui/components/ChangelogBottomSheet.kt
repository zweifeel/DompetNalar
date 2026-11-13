package com.example.ui.components
import com.example.util.AppStrings

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CorporateFare
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("changelog_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(FinSmartPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = AppStrings.t("Catatan Rilis (Changelog)", "Release Notes (Changelog)"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Versi Terbaru v1.0.16 (Build 16)",
                            fontSize = 11.sp,
                            color = IncomeGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = com.example.util.AppStrings.t("Tutup", "Close"))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Version Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = AppStrings.t("DompetNalar v1.0.16 Siap Digunakan", "DompetNalar v1.0.16 Ready to Use"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = AppStrings.t("Pembaruan ini mencakup eliminasi crash bahasa Android 14, perapian UI Mutasi & kartu arus kas, tombol tanggal Debt Planner, dan kestabilan sistem.", "This update includes Android 14 language crash fixes, Transactions UI & cashflow card polishing, Debt Planner date buttons, and overall system stability."),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Changelog Items
            Text(
                text = AppStrings.t("Daftar Pembaruan & Fitur Baru (v1.0.16):", "Updates & New Features (v1.0.16):"),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(10.dp))

            ChangelogItemRow(
                icon = Icons.Default.Language,
                title = AppStrings.t("Eliminasi Crash Bahasa & Reaktif Murni", "Zero Crash Language Switching & Pure Reactive UI"),
                description = AppStrings.t(
                    "Pergantian bahasa pada Android 14 (Realme UI / ColorOS) kini berjalan instan tanpa kedipan dan 100% bebas dari force close berkat arsitektur reaktif StateFlow dan penanganan Activity lifecycle yang aman.",
                    "Language switching on Android 14 (Realme UI / ColorOS) now runs instantly without flickering and 100% free of force closes via reactive StateFlow and safe Activity lifecycle configuration."
                )
            )

            ChangelogItemRow(
                icon = Icons.Default.PieChart,
                title = AppStrings.t("Perapian UI Mutasi & Terjemahan Kategori", "Polished Transactions UI & Category Localization"),
                description = AppStrings.t(
                    "Kartu ringkasan arus kas kini tampil proporsional tanpa teks terpotong, badge Net tampil rapi di berbagai ukuran layar, serta seluruh kategori pengeluaran/pemasukan diterjemahkan dinamis.",
                    "Cashflow summary card now displays proportionally without text truncation, Net badge aligns cleanly across screen sizes, and transaction categories are dynamically translated."
                )
            )

            ChangelogItemRow(
                icon = Icons.Default.CreditCard,
                title = AppStrings.t("Perencana Hutang & Tombol Tanggal Proporsional", "AI Debt Planner & Proportional Date Shortcuts"),
                description = AppStrings.t(
                    "Tombol pintasan tanggal jatuh tempo [Day 5] [Day 10] [Day 15] [Day 20] [End] kini tampil penuh dan proporsional, serta teks awal due date otomatis menyesuaikan bahasa aktif.",
                    "Due date shortcut buttons [Day 5] [Day 10] [Day 15] [Day 20] [End] are fully rendered and proportional, with due date default text adapting to the active language."
                )
            )

            ChangelogItemRow(
                icon = Icons.Default.AutoAwesome,
                title = AppStrings.t("Input Transaksi Bahasa Alami AI & Backdate", "AI Natural Language & Backdate Input"),
                description = AppStrings.t(
                    "Chatbot kini mampu mengenali konteks tanggal relatif ('kemarin', 'lusa', '3 hari lalu', 'tadi pagi', 'minggu lalu') dan tanggal spesifik ('tanggal 15 September', '12/09/2026') lalu otomatis menetapkan tanggal transaksi secara akurat.",
                    "Chatbot can now recognize relative dates ('yesterday', 'tomorrow', '3 days ago', 'this morning', 'last week') and specific dates ('September 15', '12/09/2026') and accurately set transaction dates automatically."
                )
            )

            ChangelogItemRow(
                icon = Icons.Default.Language,
                title = AppStrings.t("Otomatisasi Bahasa & Multi-Currency Selector", "Language Automation & Multi-Currency Selector"),
                description = AppStrings.t(
                    "Dukungan bahasa aplikasi mengikuti sistem HP (Locale default), serta pemilih mata uang global (IDR Rp, USD $, EUR €, SGD S$, MYR RM) yang memperbarui format angka secara real-time di seluruh layar.",
                    "App language follows device system locale, plus a global currency selector (IDR Rp, USD $, EUR €, SGD S$, MYR RM) updating numeric formatting in real-time across all screens."
                )
            )

            ChangelogItemRow(
                icon = Icons.Default.CreditCard,
                title = AppStrings.t("Manajemen Hutang & Tanggal Jatuh Tempo", "Debt Management & Due Dates"),
                description = AppStrings.t(
                    "Tambah ikon edit & hapus per-item hutang, opsi hapus seluruh kategori hutang, pemilihan tanggal jatuh tempo (due date), serta tombol Reset Semua Hutang (Fresh Start).",
                    "Added edit & delete icons per debt item, option to delete entire debt categories, due date picker, and Reset All Debts (Fresh Start) button."
                )
            )

            ChangelogItemRow(
                icon = Icons.Default.CorporateFare,
                title = AppStrings.t("Asset & Depreciating Asset Tracker Fleksibel", "Flexible Asset & Depreciation Tracker"),
                description = AppStrings.t(
                    "Kontrol edit & hapus item aset fisik, hapus kategori aset sekaligus, kalkulasi depresiasi nilai otomatis, dan tombol Reset Semua Aset.",
                    "Edit & delete controls for physical assets, category-wide asset deletion, automatic value depreciation, and Reset All Assets button."
                )
            )

            ChangelogItemRow(
                icon = Icons.Default.Calculate,
                title = AppStrings.t("Split Bill Ringkas & Cepat", "Quick & Compact Split Bill"),
                description = AppStrings.t(
                    "Daftar item pada kalkulator utama dibatasi 2-3 item dengan tombol 'Lihat Selengkapnya / Riwayat' agar tombol 'Kirim WA' terlihat langsung tanpa perlu scroll panjang.",
                    "Item list on main calculator limited to 2-3 items with 'View All / History' button so 'Send WA' button is immediately visible without scrolling."
                )
            )

            ChangelogItemRow(
                icon = Icons.Default.PieChart,
                title = AppStrings.t("Visualisasi Pie / Donut Chart di Analisis", "Pie / Donut Chart Visualization in Analysis"),
                description = AppStrings.t(
                    "Tambahan mode grafik lingkaran (Donut/Pie Chart) pada Analisis Arus Kas untuk memvisualisasikan proporsi pengeluaran per kategori secara interaktif.",
                    "Added donut/pie chart visualization to Cashflow Analysis to view interactive category expense proportions."
                )
            )

            ChangelogItemRow(
                icon = Icons.Default.Security,
                title = AppStrings.t("Pembersihan Pengaturan & Cloud Sync", "Settings Cleanup & Cloud Sync"),
                description = AppStrings.t(
                    "Menu preferensi lebih bersih, integrasi sinkronisasi Google Drive Folder 'DompetNalar/App Updates', dan menu Cek Pembaruan Versi otomatis.",
                    "Cleaner preferences menu, Google Drive integration ('DompetNalar/App Updates'), and automated Version Update Check."
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary)
            ) {
                Text(AppStrings.t("Tutup Catatan Rilis", "Close Release Notes"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ChangelogItemRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(FinSmartPrimary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
