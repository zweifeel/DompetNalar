package com.example.ui.components

import com.example.util.AppStrings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary

@Composable
fun InitialMasterSetupDialog(
    authError: String?,
    onDismiss: () -> Unit,
    onSave: (newPassword: String, confirmPassword: String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val displayError = localError ?: authError

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dialog_initial_master_setup"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = FinSmartPrimary
                )
                Text(
                    text = AppStrings.t("Buat Kata Sandi Master Dev", "Create Master Dev Password"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = FinSmartPrimary.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = AppStrings.t(
                                "👑 Pengaturan Awal Master Developer (${com.example.config.AppConfig.APP_AUTHOR}):",
                                "👑 Initial Master Developer Setup (${com.example.config.AppConfig.APP_AUTHOR}):"
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = FinSmartPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = AppStrings.t(
                                "Aplikasi mendeteksi instalasi baru. Demi keamanan, silakan buat kata sandi utama untuk akun Master Developer Anda. Kata sandi ini akan di-hash secara aman menggunakan PBKDF2.",
                                "The app detected a fresh installation. For security, please set a master password for your Master Developer account. This password will be hashed securely using PBKDF2."
                            ),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        localError = null
                    },
                    label = { Text(AppStrings.t("Kata Sandi Baru", "New Password")) },
                    placeholder = { Text(AppStrings.t("Minimal 6 karakter", "Minimum 6 characters")) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (newPasswordVisible) AppStrings.t("Sembunyikan kata sandi", "Hide password") else AppStrings.t("Tampilkan kata sandi", "Show password")
                            )
                        }
                    },
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_new_master_password")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                    },
                    label = { Text(com.example.util.AppStrings.t("Konfirmasi Kata Sandi", "Confirm Password")) },
                    placeholder = { Text(AppStrings.t("Ulangi kata sandi baru", "Repeat new password")) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (confirmPasswordVisible) AppStrings.t("Sembunyikan kata sandi", "Hide password") else AppStrings.t("Tampilkan kata sandi", "Show password")
                            )
                        }
                    },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_confirm_master_password")
                )

                displayError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = ExpenseRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedNew = newPassword.trim()
                    val trimmedConfirm = confirmPassword.trim()
                    if (trimmedNew.isEmpty()) {
                        localError = AppStrings.t("Kata sandi tidak boleh kosong.", "Password cannot be empty.")
                        return@Button
                    }
                    if (trimmedNew.length < 6) {
                        localError = AppStrings.t("Kata sandi minimal 6 karakter.", "Password must be at least 6 characters.")
                        return@Button
                    }
                    if (trimmedNew != trimmedConfirm) {
                        localError = AppStrings.t("Konfirmasi kata sandi tidak cocok.", "Password confirmation does not match.")
                        return@Button
                    }
                    localError = null
                    onSave(trimmedNew, trimmedConfirm)
                },
                colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                modifier = Modifier.testTag("btn_save_initial_master_password")
            ) {
                Text(com.example.util.AppStrings.t("Simpan & Masuk", "Save & Sign In"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_initial_master_password")
            ) {
                Text(com.example.util.AppStrings.cancel)
            }
        }
    )
}
