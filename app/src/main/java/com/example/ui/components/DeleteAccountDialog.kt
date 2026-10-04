package com.example.ui.components

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
    onConfirmDelete: (password: String) -> Boolean
) {
    var passwordInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isGuest = currentUser == null || currentUser.isGuest

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
                        text = "Hapus Akun & Data Permanen",
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
                            text = "⚠️ PERINGATAN (Play Store Deletion Policy):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ExpenseRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isGuest) {
                                "Tindakan ini tidak dapat dibatalkan. Seluruh riwayat mutasi, data saldo dompet, dan data lokal sesi tamu akan dihapus secara permanen dari perangkat dan database."
                            } else {
                                "Tindakan ini tidak dapat dibatalkan. Seluruh riwayat mutasi, data saldo dompet, preferensi, dan kredensial akun '${currentUser?.username}' akan dihapus secara permanen dari perangkat dan database."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isGuest) {
                    Text(
                        text = "Sesi Mode Tamu tidak memerlukan kata sandi. Mengonfirmasi tindakan ini akan langsung menghapus seluruh riwayat mutasi, dompet, dan data lokal secara permanen dari perangkat.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Konfirmasi Kata Sandi / PIN:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            errorMessage = null
                        },
                        label = { Text("Masukkan kata sandi akun") },
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

                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = err, color = ExpenseRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isGuest && passwordInput.isBlank()) {
                        errorMessage = "Kata sandi wajib diisi untuk konfirmasi keamanan."
                        return@Button
                    }
                    val isSuccess = onConfirmDelete(if (isGuest) "" else passwordInput.trim())
                    if (!isSuccess) {
                        errorMessage = if (isGuest) "Penghapusan akun dibatalkan." else "Kata sandi salah. Penghapusan akun dibatalkan."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                modifier = Modifier.testTag("btn_confirm_delete_account_final")
            ) {
                Text("Hapus Permanen", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
