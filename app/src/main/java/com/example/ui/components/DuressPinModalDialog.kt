package com.example.ui.components
import com.example.util.AppStrings

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserGamificationState
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuressPinModalDialog(
    state: UserGamificationState,
    onDismiss: () -> Unit,
    onSavePins: (mainPin: String, duressPin: String, biometricEnabled: Boolean) -> Unit,
    onVerifyPinAndUnlock: (inputPin: String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var mainPinInput by remember { mutableStateOf(state.mainPin) }
    var duressPinInput by remember { mutableStateOf(state.duressPin) }
    var biometricEnabled by remember { mutableStateOf(state.isBiometricEnabled) }
    var showPins by remember { mutableStateOf(false) }

    var testInputPin by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("duress_pin_modal")
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
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SECURITY SUITE & DURESS PIN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = FinSmartPrimary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = com.example.util.AppStrings.t("Tutup", "Close"))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = com.example.util.AppStrings.t("🔒 Proteksi Keamanan Ganda", "🔒 Dual Security Protection"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = com.example.util.AppStrings.t(
                            "• PIN Utama (6-Digit): Membuka data keuangan asli Anda.\n• Duress PIN (PIN Penyamaran): Membuka aplikasi dalam 'Mode Stealth' (Data Palsu) untuk perlindungan dari paksaan.",
                            "• Main PIN (6-Digit): Unlocks your real financial data.\n• Duress PIN (Coercion PIN): Opens the app in 'Stealth Mode' (Decoy Data) for protection under duress."
                        ),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main PIN Input
            OutlinedTextField(
                value = mainPinInput,
                onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) mainPinInput = it },
                label = { Text(AppStrings.t("PIN Utama (Akun Asli)", "Main PIN (Real Account)")) },
                visualTransformation = if (showPins) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Duress PIN Input
            OutlinedTextField(
                value = duressPinInput,
                onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) duressPinInput = it },
                label = { Text(com.example.util.AppStrings.t("Duress PIN (Mode Stealth / Kamuflase)", "Duress PIN (Stealth / Decoy Mode)")) },
                visualTransformation = if (showPins) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = ExpenseRed) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = com.example.util.AppStrings.t("Aktifkan Biometric (Fingerprint)", "Enable Biometrics (Fingerprint)"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = { biometricEnabled = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (mainPinInput.length < 6 || duressPinInput.length < 6) {
                        Toast.makeText(context, com.example.util.AppStrings.t("PIN wajib 6-digit angka!", "PIN must be a 6-digit number!"), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (mainPinInput == duressPinInput) {
                        Toast.makeText(context, com.example.util.AppStrings.t("PIN Utama & Duress PIN tidak boleh sama!", "Main PIN and Duress PIN cannot be the same!"), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSavePins(mainPinInput, duressPinInput, biometricEnabled)
                    Toast.makeText(context, com.example.util.AppStrings.t("Pengaturan Keamanan & Duress PIN Disimpan!", "Security & Duress PIN Settings Saved!"), Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary)
            ) {
                Text(com.example.util.AppStrings.t("Simpan Pengaturan PIN", "Save PIN Settings"), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Test PIN Simulation Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🧪 Tes Simulasi Input PIN:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = testInputPin,
                            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) testInputPin = it },
                            placeholder = { Text(AppStrings.t("Masukkan PIN...", "Enter PIN...")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                onVerifyPinAndUnlock(testInputPin)
                                testInputPin = ""
                                onDismiss()
                            },
                            enabled = testInputPin.length == 6,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Tes Unlock", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
