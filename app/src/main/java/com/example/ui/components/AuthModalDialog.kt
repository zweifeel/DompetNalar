package com.example.ui.components
import com.example.util.AppStrings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthModalDialog(
    pendingOtpCode: String?,
    authError: String?,
    isGoogleLoading: Boolean = false,
    onDismiss: () -> Unit,
    onLogin: (identifier: String, pass: String) -> Unit,
    onSendOtp: (email: String) -> Unit,
    onVerifyOtpAndRegister: (username: String, email: String, pass: String, otp: String) -> Unit,
    onGoogleLogin: () -> Unit,
    onGuestLogin: () -> Unit,
    onClearError: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isLoginTab by remember { mutableStateOf(true) }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Registration fields
    var regUsername by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regOtp by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isGoogleLoading) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("auth_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Modern FinSmart Brand Logo with glowing gradient
            FinSmartLogo(
                size = 64.dp,
                showGlow = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isLoginTab) AppStrings.t("Masuk ke DompetNalar", "Log In to DompetNalar") else AppStrings.t("Daftar Akun Baru", "Create New Account"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = AppStrings.t("Kelola finansial cerdas dengan sinkronisasi & AI", "Manage smart finances with sync & AI"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Tab Row (Masuk / Daftar)
            TabRow(
                selectedTabIndex = if (isLoginTab) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .padding(4.dp),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier
                            .tabIndicatorOffset(tabPositions[if (isLoginTab) 0 else 1])
                            .clip(RoundedCornerShape(12.dp)),
                        color = FinSmartPrimary,
                        height = 3.dp
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = isLoginTab,
                    onClick = {
                        isLoginTab = true
                        isOtpSent = false
                    },
                    text = {
                        Text(
                            AppStrings.t("Masuk (Login)", "Log In"),
                            fontWeight = if (isLoginTab) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLoginTab) FinSmartPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Tab(
                    selected = !isLoginTab,
                    onClick = { isLoginTab = false },
                    text = {
                        Text(
                            AppStrings.t("Daftar Akun", "Register"),
                            fontWeight = if (!isLoginTab) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isLoginTab) FinSmartPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoginTab) {
                // LOGIN FORM
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text(com.example.util.AppStrings.t("Email atau Username", "Email or Username")) },
                    placeholder = { Text(com.example.util.AppStrings.t("misal: user123 atau nama@email.com", "e.g. user123 or name@email.com")) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_login_identifier")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(com.example.util.AppStrings.t("Kata Sandi", "Password")) },
                    placeholder = { Text("••••••••") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_login_password")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (identifier.isNotBlank()) {
                            onLogin(identifier, password)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_submit_login"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(com.example.util.AppStrings.t("Masuk Sekarang", "Sign In Now"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            } else {
                // REGISTRATION FORM WITH OTP VERIFICATION
                if (!isOtpSent) {
                    OutlinedTextField(
                        value = regUsername,
                        onValueChange = { regUsername = it },
                        label = { Text(com.example.util.AppStrings.t("Nama Lengkap / Panggilan", "Full Name / Nickname")) },
                        placeholder = { Text(com.example.util.AppStrings.t("misal: Nama Lengkap", "e.g. Full Name")) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_reg_username")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = regEmail,
                        onValueChange = { regEmail = it },
                        label = { Text(com.example.util.AppStrings.t("Alamat Email", "Email Address")) },
                        placeholder = { Text("nama@email.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_reg_email")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = regPassword,
                        onValueChange = { regPassword = it },
                        label = { Text(com.example.util.AppStrings.t("Buat Kata Sandi", "Create Password")) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_reg_password")
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (regEmail.isNotBlank()) {
                                onSendOtp(regEmail)
                                isOtpSent = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_send_otp"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(com.example.util.AppStrings.t("Kirim Kode OTP Verifikasi", "Send Verification OTP Code"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    // OTP VERIFICATION STEP
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = AppStrings.t("Kode OTP telah dikirim ke:", "OTP code has been sent to:"),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = regEmail,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            pendingOtpCode?.let { code ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = AppStrings.t("💡 Kode OTP Simulasi: $code", "💡 Simulated OTP Code: $code"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = regOtp,
                        onValueChange = { regOtp = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text(com.example.util.AppStrings.t("Masukkan 6-Digit Kode OTP", "Enter 6-Digit OTP Code")) },
                        placeholder = { Text("123456") },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_otp_code")
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            onVerifyOtpAndRegister(regUsername, regEmail, regPassword, regOtp)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_verify_otp_submit"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(com.example.util.AppStrings.t("Verifikasi & Aktifkan Akun", "Verify & Activate Account"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    TextButton(onClick = { isOtpSent = false }) {
                        Text(com.example.util.AppStrings.t("← Ubah Email / Data", "← Change Email / Data"), fontSize = 12.sp)
                    }
                }
            }

            // Divider Or
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Text(
                    text = AppStrings.t("  atau  ", "  or  "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Google OAuth & Guest Mode options
            OutlinedButton(
                onClick = onGoogleLogin,
                enabled = !isGoogleLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_google_oauth"),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isGoogleLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Menghubungkan Akun Google...", "Connecting Google Account..."), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                } else {
                    Text(com.example.util.AppStrings.t("Masuk dengan Akun Google", "Sign In with Google Account"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            authError?.let { err ->
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = ExpenseRed.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = ExpenseRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = AppStrings.t("Kendala Autentikasi / Google Sign-In", "Authentication Issue / Google Sign-In"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ExpenseRed
                                )
                            }
                            if (onClearError != null) {
                                IconButton(
                                    onClick = onClearError,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = AppStrings.t("Tutup pesan", "Dismiss message"),
                                        tint = ExpenseRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = err,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onGuestLogin,
                modifier = Modifier.testTag("btn_guest_mode")
            ) {
                Text(
                    AppStrings.t("Coba Mode Tamu (Guest Mode)", "Try Guest Mode"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
