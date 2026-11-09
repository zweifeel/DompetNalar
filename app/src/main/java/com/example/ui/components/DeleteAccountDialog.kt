package com.example.ui.components
import com.example.util.AppStrings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.User
import com.example.ui.theme.ExpenseRed

@Composable
fun DeleteAccountDialog(
    currentUser: User?,
    onDismiss: () -> Unit,
    errorMessage: String? = null,
    onConfirmDelete: (password: String) -> Boolean
) {
    var passwordInput by remember { mutableStateOf("") }
    var localErrorMessage by remember { mutableStateOf<String?>(null) }
    val displayError = errorMessage ?: localErrorMessage
    val isGuest = currentUser == null || currentUser.isGuest
    val requiresPassword = !isGuest && !currentUser.passwordHash.isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = ExpenseRed
                    )
                    Text(
                        text = AppStrings.t("Hapus Akun & Data Permanen", "Delete Account & Data Permanently"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = ExpenseRed
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = AppStrings.t("⚠️ PERINGATAN (Play Store Deletion Policy):", "⚠️ WARNING (Play Store Deletion Policy):"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ExpenseRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isGuest) {
                                "Tindakan ini tidak dapat dibatalkan. Seluruh riwayat mutasi, data saldo dompet, dan data lokal sesi tamu akan dihapus secara permanen dari perangkat dan database."
                            } else {
                                "Tindakan ini tidak dapat dibatalkan. Seluruh riwayat mutasi, data saldo dompet, preferensi, dan kredensial akun '${currentUser?.username ?: currentUser?.email}' akan dihapus secara permanen dari perangkat, database lokal, dan server cloud."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isGuest) {
                    Text(
                        text = AppStrings.t("Sesi Mode Tamu tidak memerlukan kata sandi. Mengonfirmasi tindakan ini akan langsung menghapus seluruh riwayat mutasi, dompet, dan data lokal secara permanen dari perangkat.", "Guest mode session does not require a password. Confirming will permanently delete all transaction history, wallets, and local data from this device."),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!requiresPassword) {
                    // Google Sign-In or OAuth account without local password
                    Text(
                        text = AppStrings.t("Akun Anda terhubung melalui Google Sign-In (${currentUser.email}). Tidak ada kata sandi lokal yang diperlukan. Mengonfirmasi tindakan ini akan menghapus akun Google Anda dari DompetNalar dan server cloud secara permanen beserta seluruh data keuangan lokal Anda.", "Your account is connected via Google Sign-In (${currentUser.email}). No local password required. Confirming will permanently delete your Google account and all local financial data from DompetNalar."),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = AppStrings.t("Konfirmasi Kata Sandi Akun:", "Confirm Account Password:"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            localErrorMessage = null
                        },
                        label = { Text(AppStrings.t("Masukkan kata sandi akun", "Enter account password")) },
                        placeholder = { Text("••••••••") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_confirm_delete_account_password")
                    )
                }

                displayError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = err, color = ExpenseRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (requiresPassword && passwordInput.isBlank()) {
                        localErrorMessage = "Kata sandi wajib diisi untuk konfirmasi keamanan."
                        return@Button
                    }
                    val isSuccess = onConfirmDelete(if (requiresPassword) passwordInput.trim() else "")
                    if (!isSuccess && localErrorMessage == null) {
                        localErrorMessage = if (requiresPassword) "Kata sandi salah. Penghapusan akun dibatalkan." else "Gagal menghapus akun. Silakan coba beberapa saat lagi."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                modifier = Modifier.testTag("btn_confirm_delete_account_final")
            ) {
                Text(com.example.util.AppStrings.t("Hapus Permanen", "Delete Permanently"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(com.example.util.AppStrings.cancel)
            }
        }
    )
}
