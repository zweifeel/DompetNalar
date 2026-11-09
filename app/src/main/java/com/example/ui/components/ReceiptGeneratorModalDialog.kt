package com.example.ui.components
import com.example.util.AppStrings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.util.CurrencyFormatter
import com.example.util.ReceiptFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptGeneratorModalDialog(
    transaction: TransactionItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Visual width mode: false = 58mm (default portable format), true = 80mm (wide desktop format)
    var is80mm by remember { mutableStateOf(false) }

    val reference = ReceiptFormatter.formatReceiptReference(transaction.id)
    val dateString = ReceiptFormatter.formatTransactionDate(transaction.timestamp)
    val typeLabel = ReceiptFormatter.getTransactionTypeLabel(transaction.type)
    val formattedAmount = CurrencyFormatter.formatRupiah(transaction.amount)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("receipt_generator_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Badge & Dismiss Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = FinSmartPrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            tint = FinSmartPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = AppStrings.t("BUKTI TRANSAKSI DIGITAL", "DIGITAL TRANSACTION RECEIPT"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = FinSmartPrimary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 58mm / 80mm Visual Format Switcher
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (!is80mm) FinSmartPrimary else Color.Transparent,
                    modifier = Modifier.clickable { is80mm = false }
                ) {
                    Text(
                        text = "Format 58mm",
                        fontSize = 11.sp,
                        fontWeight = if (!is80mm) FontWeight.Bold else FontWeight.Normal,
                        color = if (!is80mm) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (is80mm) FinSmartPrimary else Color.Transparent,
                    modifier = Modifier.clickable { is80mm = true }
                ) {
                    Text(
                        text = "Format 80mm",
                        fontSize = 11.sp,
                        fontWeight = if (is80mm) FontWeight.Bold else FontWeight.Normal,
                        color = if (is80mm) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Monospace Receipt View (Personal Finance)
            Card(
                modifier = Modifier
                    .then(if (is80mm) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.9f))
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                border = BorderStroke(1.dp, Color.LightGray)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = AppStrings.t("BUKTI TRANSAKSI DIGITAL", "DIGITAL TRANSACTION RECEIPT"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (is80mm) 15.sp else 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black
                    )
                    Text(
                        text = "DOMPETNALAR PERSONAL FINANCE",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.DarkGray
                    )
                    Text(
                        text = "Manajemen Keuangan Pribadi Cerdas",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (is80mm) "------------------------------------------" else "--------------------------------",
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )

                    // Reference & Timestamp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "No. Bukti:", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)
                        Text(text = reference, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Tanggal:", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)
                        Text(text = dateString, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Black)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Jenis:", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)
                        Text(text = typeLabel, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = Color.Black)
                    }

                    Text(
                        text = if (is80mm) "------------------------------------------" else "--------------------------------",
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )

                    // Transaction details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = AppStrings.t("Deskripsi:", "Description:"), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)
                        Text(text = transaction.title, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = AppStrings.t("Kategori:", "Category:"), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)
                        Text(text = transaction.category, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Black)
                    }

                    if (transaction.type == TransactionType.TRANSFER) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Dari:", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)
                            Text(text = transaction.walletName, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Black)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Ke:", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)
                            Text(text = transaction.targetWalletName ?: "Dompet Tujuan", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Black)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Sumber Dana:", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)
                            Text(text = transaction.walletName, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Black)
                        }
                    }

                    if (transaction.note.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = AppStrings.t("Catatan:", "Notes:"), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)
                            Text(text = transaction.note, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (is80mm) "------------------------------------------" else "--------------------------------",
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )

                    // Total Row with Type Distinction
                    val totalLabel = when (transaction.type) {
                        TransactionType.EXPENSE -> "TOTAL PENGELUARAN:"
                        TransactionType.INCOME -> "TOTAL PEMASUKAN:"
                        TransactionType.TRANSFER -> "TOTAL TRANSFER:"
                    }
                    val amountColor = when (transaction.type) {
                        TransactionType.EXPENSE -> ExpenseRed
                        TransactionType.INCOME -> IncomeGreen
                        TransactionType.TRANSFER -> FinSmartPrimary
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = totalLabel,
                            fontSize = if (is80mm) 12.sp else 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                        Text(
                            text = formattedAmount,
                            fontSize = if (is80mm) 14.sp else 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = amountColor
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = AppStrings.t("Simpan bukti ini sebagai referensi\npencatatan keuangan pribadi Anda.\n--- DompetNalar Personal Finance ---", "Save this receipt for your\npersonal finance records.\n--- DompetNalar Personal Finance ---"),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: Real Android Share (ACTION_SEND) + Clipboard Copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val shareText = ReceiptFormatter.formatReceiptText(transaction, is80mm)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(AppStrings.t("Bukti Transaksi DompetNalar", "DompetNalar Transaction Receipt"), shareText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, AppStrings.t("Teks bukti transaksi berhasil disalin!", "Receipt text copied successfully!"), Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salin Teks", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        val shareText = ReceiptFormatter.formatReceiptText(transaction, is80mm)
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        val chooser = Intent.createChooser(sendIntent, AppStrings.t("Bagikan Bukti Transaksi DompetNalar", "Share DompetNalar Transaction Receipt"))
                        context.startActivity(chooser)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bagikan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
