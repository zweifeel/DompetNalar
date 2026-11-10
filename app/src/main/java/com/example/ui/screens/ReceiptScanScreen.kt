package com.example.ui.screens
import com.example.util.AppStrings
import com.example.util.LocaleHelper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.ai.ReceiptAiOcrScanner
import com.example.data.model.AccountMode
import com.example.data.model.CategoryItem
import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import com.example.data.model.TaxTreatment
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.util.CurrencyFormatter
import com.example.util.ReceiptCalculator
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScanScreen(
    wallets: List<Wallet>,
    categories: List<CategoryItem>,
    accountMode: AccountMode,
    isUnlocked: Boolean,
    onTriggerPaywall: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenSplitBill: (ReceiptScanResult) -> Unit,
    onSaveTransaction: (
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        categoryIcon: String,
        walletId: Long,
        walletName: String,
        targetWalletId: Long?,
        targetWalletName: String?,
        timestamp: Long,
        note: String,
        tags: String,
        onSuccess: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var scannedResult by remember { mutableStateOf<ReceiptScanResult?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var activeScanToken by remember { mutableStateOf(0L) }
    var tempCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val tempCameraUri = tempCameraUriString?.let { Uri.parse(it) }

    // Form fields for editing scanned receipt
    var editMerchantName by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("Makanan & Minuman") }
    var editSubtotal by remember { mutableStateOf(0.0) }
    var editTax by remember { mutableStateOf(0.0) }
    var editService by remember { mutableStateOf(0.0) }
    var editDiscount by remember { mutableStateOf(0.0) }
    var editTotalAmount by remember { mutableStateOf(0.0) }
    var editTaxTreatment by remember { mutableStateOf(TaxTreatment.UNKNOWN) }
    var editItems by remember { mutableStateOf<List<ReceiptItem>>(emptyList()) }
    var editRawText by remember { mutableStateOf("") }
    var selectedWallet by remember { mutableStateOf(wallets.firstOrNull()) }

    // Add/Edit Item Dialog state
    var isItemDialogOpen by remember { mutableStateOf(false) }
    var editingItemName by remember { mutableStateOf("") }
    var editingItemPrice by remember { mutableStateOf("") }
    var editingItemQty by remember { mutableStateOf("1") }
    var editingItemIndex by remember { mutableStateOf(-1) }

    // Edit Discount Dialog state
    var isDiscountDialogOpen by remember { mutableStateOf(false) }
    var editingDiscountText by remember { mutableStateOf("") }

    fun syncScanResultToForm(result: ReceiptScanResult) {
        scannedResult = result
        editMerchantName = result.merchantName
        editDate = result.date
        editCategory = result.category
        editSubtotal = result.subtotal
        editTax = result.tax
        editService = result.serviceCharge
        editDiscount = result.discount
        editTaxTreatment = result.taxTreatment
        editTotalAmount = result.totalAmount
        editItems = result.items
        editRawText = result.rawText ?: ""
        selectedWallet = wallets.firstOrNull()
    }

    fun recalculateTotals(
        items: List<ReceiptItem>,
        tax: Double,
        service: Double,
        discount: Double,
        taxTreatment: TaxTreatment
    ) {
        val (newSub, newTotal) = ReceiptCalculator.recalculate(
            items = items,
            tax = tax,
            service = service,
            discount = discount,
            taxTreatment = taxTreatment,
            fallbackTotal = editTotalAmount
        )
        editSubtotal = newSub
        editTotalAmount = newTotal
    }

    fun processReceipt(bitmap: Bitmap?, uri: Uri?) {
        if (!isUnlocked) {
            onTriggerPaywall()
            return
        }

        if (isScanning) return // Cegah eksekusi ganda jika tombol ditekan berulang

        val token = System.currentTimeMillis()
        activeScanToken = token

        coroutineScope.launch {
            isScanning = true
            scanErrorMessage = null
            try {
                // Batasi waktu pemindaian nota hingga maksimal 60 detik (P1 Timeout Requirement)
                val result = withTimeoutOrNull(60_000L) {
                    ReceiptAiOcrScanner.scanReceiptImage(context, uri, bitmap)
                }
                if (activeScanToken == token) {
                    if (result != null) {
                        syncScanResultToForm(result)
                        Toast.makeText(context, "${com.example.util.AppStrings.t("Nota", "Receipt")} '${result.merchantName}' ${com.example.util.AppStrings.t("berhasil ditranskripsikan!", "transcribed successfully!")}", Toast.LENGTH_SHORT).show()
                    } else {
                        scanErrorMessage = if (com.example.util.LocaleHelper.isEnglish()) {
                            "Receipt scan timed out (60-second limit). Please ensure the receipt is well-lit, clearly readable, and try again."
                        } else {
                            "Pemindaian nota melebihi batas waktu (60 detik). Pastikan gambar nota jelas, teks terbaca, dan pencahayaan cukup, lalu coba lagi."
                        }
                    }
                }
            } catch (e: Exception) {
                if (activeScanToken == token) {
                    scanErrorMessage = e.message ?: "Gagal mentranskripsi nota. Pastikan gambar nota jelas dan pencahayaan cukup."
                }
            } finally {
                if (activeScanToken == token) {
                    isScanning = false
                }
            }
        }
    }

    // Camera Capture Launcher dengan verifikasi FileProvider & fallback hasil
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = tempCameraUriString?.let { Uri.parse(it) }
        val hasFileContent = if (uri != null) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    pfd.statSize > 0
                } ?: false
            } catch (e: Exception) {
                false
            }
        } else false

        if (result.resultCode == android.app.Activity.RESULT_OK || hasFileContent) {
            if (uri != null && hasFileContent) {
                selectedImageUri = uri
                capturedBitmap = null
                processReceipt(null, uri)
            } else {
                Toast.makeText(context, com.example.util.AppStrings.t("Foto dari kamera tidak tersimpan. Silakan coba lagi.", "Photo from camera was not saved. Please try again."), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, com.example.util.AppStrings.t("Pengambilan foto kamera dibatalkan.", "Camera photo capture cancelled."), Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraSafely() {
        try {
            val packageManager = context.packageManager
            if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
                Toast.makeText(context, com.example.util.AppStrings.t("Perangkat tidak memiliki kamera. Silakan pilih foto dari galeri.", "Device does not have a camera. Please choose a photo from gallery."), Toast.LENGTH_LONG).show()
                return
            }

            val tempFile = File.createTempFile("receipt_capture_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            tempCameraUriString = uri.toString()

            val cameraIntent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
                addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("receipt_capture", uri)
            }

            val resolvedActivities = packageManager.queryIntentActivities(
                cameraIntent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resInfo in resolvedActivities) {
                val pkgName = resInfo.activityInfo.packageName
                context.grantUriPermission(
                    pkgName,
                    uri,
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            if (cameraIntent.resolveActivity(packageManager) != null || resolvedActivities.isNotEmpty()) {
                cameraLauncher.launch(cameraIntent)
            } else {
                Toast.makeText(context, com.example.util.AppStrings.t("Aplikasi kamera tidak ditemukan di perangkat ini. Silakan pilih foto dari galeri.", "Camera app not found on this device. Please choose a photo from gallery."), Toast.LENGTH_LONG).show()
            }
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(context, com.example.util.AppStrings.t("Aplikasi kamera tidak ditemukan di perangkat ini. Silakan pilih foto dari galeri.", "Camera app not found on this device. Please choose a photo from gallery."), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "${com.example.util.AppStrings.t("Gagal menyiapkan kamera:", "Failed to initialize camera:")} ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraSafely()
        } else {
            Toast.makeText(context, com.example.util.AppStrings.t("Izin kamera diperlukan untuk mengambil foto nota.", "Camera permission is required to take receipt photos."), Toast.LENGTH_LONG).show()
        }
    }

    // Gallery Photo Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            capturedBitmap = null
            processReceipt(null, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = AppStrings.t("Scan Nota", "Scan Receipt"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = FinSmartPrimary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "DOMPETNALAR OCR",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {
            // Hero Intro Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(FinSmartPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = AppStrings.t("Scan Nota Otomatis & Rinci", "Automatic & Detailed Receipt Scan"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = AppStrings.t("Foto nota belanjaanmu, AI mengekstrak item & total secara instan.", "Take a photo of your receipt, AI instantly extracts items & total."),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Capture & Upload Options
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Camera Button
                    Button(
                        onClick = {
                            if (!isUnlocked) {
                                onTriggerPaywall()
                                return@Button
                            }
                            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                launchCameraSafely()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("btn_take_photo_receipt"),
                        colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.t("Foto Kamera", "Camera Photo"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Gallery Button
                    OutlinedButton(
                        onClick = {
                            if (!isUnlocked) {
                                onTriggerPaywall()
                                return@OutlinedButton
                            }
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("btn_pick_gallery_receipt"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.t("Dari Galeri", "From Gallery"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Quick Demo Presets (for fast instant testing)
            item {
                Column {
                    Text(
                        text = AppStrings.t("Atau coba contoh simulasi nota (Demo):", "Or try sample receipt simulations (Demo):"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1.05f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val preset = ReceiptAiOcrScanner.generateRealisticReceiptFallback(presetType = 0)
                                    syncScanResultToForm(preset)
                                }
                                .testTag("btn_preset_cafe"),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.LocalCafe, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Cafe/Kopi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1.18f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val preset = ReceiptAiOcrScanner.generateRealisticReceiptFallback(presetType = 1)
                                    syncScanResultToForm(preset)
                                }
                                .testTag("btn_preset_mart"),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Indomaret", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(0.77f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val preset = ReceiptAiOcrScanner.generateRealisticReceiptFallback(presetType = 2)
                                    syncScanResultToForm(preset)
                                }
                                .testTag("btn_preset_restaurant"),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Restaurant, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Momoyo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }

            // Loading Progress
            if (isScanning) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("card_scan_loading"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = FinSmartPrimary, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = AppStrings.t("Menganalisis Struk dengan DompetNalar OCR...", "Analyzing Receipt with DompetNalar OCR..."),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = AppStrings.t("Mengonversi gambar & mengekstrak item, tanggal, pajak & total.", "Converting image & extracting items, date, tax & total."),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = FinSmartPrimary,
                                trackColor = FinSmartPrimary.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }

            // Error Banner Card
            if (scanErrorMessage != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("card_scan_error"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Kendala Pemindaian Nota",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = scanErrorMessage ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        scanErrorMessage = null
                                        if (capturedBitmap != null || selectedImageUri != null) {
                                            processReceipt(capturedBitmap, selectedImageUri)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("btn_retry_receipt_scan")
                                ) {
                                    Text(com.example.util.AppStrings.retry, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        scanErrorMessage = null
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(AppStrings.t("Pilih Foto Lain", "Choose Another Photo"), fontSize = 12.sp)
                                }
                                TextButton(
                                    onClick = { scanErrorMessage = null }
                                ) {
                                    Text(AppStrings.t("Tutup", "Close"), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Scanned Receipt Preview Card & Editor
            scannedResult?.let { result ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scanned_receipt_preview_card"),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Header: Merchant & Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(FinSmartPrimary.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Receipt, contentDescription = null, tint = FinSmartPrimary)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = editMerchantName.ifBlank { "Struk Belanja" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Tanggal: $editDate • $editCategory",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    color = IncomeGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "AI Terverifikasi",
                                        color = IncomeGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Display Thumbnail if available
                            capturedBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Nota yang di-scan",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            selectedImageUri?.let { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Nota yang di-scan",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Transkripsi Literal Teks Asli Terbaca
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_transcription_result"),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = FinSmartPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Transkripsi Teks Asli (Literal OCR)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText(AppStrings.t("Transkripsi Nota DompetNalar", "DompetNalar Receipt Transcription"), editRawText)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Teks nota berhasil disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Salin",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Salin Teks", fontSize = 11.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Hasil pembacaan teks apa adanya dari foto nota (tanpa penafsiran atau rekaan AI):",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = editRawText,
                                        onValueChange = { editRawText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_raw_transcription_text"),
                                        maxLines = 8,
                                        shape = RoundedCornerShape(10.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Form Edit: Merchant Name & Date
                            OutlinedTextField(
                                value = editMerchantName,
                                onValueChange = { editMerchantName = it },
                                label = { Text(AppStrings.t("Nama Toko / Restoran", "Store / Restaurant Name")) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = editDate,
                                    onValueChange = { editDate = it },
                                    label = { Text(AppStrings.date) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = editCategory,
                                    onValueChange = { editCategory = it },
                                    label = { Text(AppStrings.category) },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))

                            // Itemized List Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Rincian Item (${editItems.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                TextButton(
                                    onClick = {
                                        editingItemIndex = -1
                                        editingItemName = ""
                                        editingItemPrice = ""
                                        editingItemQty = "1"
                                        isItemDialogOpen = true
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(AppStrings.t("Tambah Item", "Add Item"), fontSize = 12.sp)
                                }
                            }

                            // Item List
                            editItems.forEachIndexed { index, item ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            editingItemIndex = index
                                            editingItemName = item.name
                                            editingItemPrice = if (item.price > 0) item.price.toLong().toString() else ""
                                            editingItemQty = item.qty.toString()
                                            isItemDialogOpen = true
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${item.qty}x @ ${CurrencyFormatter.formatRupiah(item.price)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Text(
                                            text = CurrencyFormatter.formatRupiah(item.total),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )

                                        IconButton(
                                            onClick = {
                                                val updated = editItems.toMutableList()
                                                updated.removeAt(index)
                                                editItems = updated
                                                recalculateTotals(updated, editTax, editService, editDiscount, editTaxTreatment)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))

                            // Breakdown Numbers (Subtotal, Tax, Service, Discount, Grand Total)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(AppStrings.t("Subtotal Item:", "Item Subtotal:"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(CurrencyFormatter.formatRupiah(editSubtotal), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            if (editTax > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val taxLabel = when (editTaxTreatment) {
                                        TaxTreatment.INCLUSIVE -> "Pajak / PPN (Sudah Termasuk):"
                                        TaxTreatment.EXCLUSIVE -> "Pajak / PB1 (Biaya Tambahan):"
                                        TaxTreatment.UNKNOWN -> "Pajak (Perlu Diperiksa):"
                                    }
                                    Text(taxLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(CurrencyFormatter.formatRupiah(editTax), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = editTaxTreatment == TaxTreatment.INCLUSIVE,
                                        onClick = {
                                            editTaxTreatment = TaxTreatment.INCLUSIVE
                                            recalculateTotals(editItems, editTax, editService, editDiscount, TaxTreatment.INCLUSIVE)
                                        },
                                        label = { Text(AppStrings.t("Pajak Termasuk (Inklusif)", "Tax Included (Inclusive)"), fontSize = 10.sp) },
                                        modifier = Modifier.testTag("chip_tax_inclusive")
                                    )
                                    FilterChip(
                                        selected = editTaxTreatment == TaxTreatment.EXCLUSIVE,
                                        onClick = {
                                            editTaxTreatment = TaxTreatment.EXCLUSIVE
                                            recalculateTotals(editItems, editTax, editService, editDiscount, TaxTreatment.EXCLUSIVE)
                                        },
                                        label = { Text(AppStrings.t("Pajak Tambahan (Eksklusif)", "Additional Tax (Exclusive)"), fontSize = 10.sp) },
                                        modifier = Modifier.testTag("chip_tax_exclusive")
                                    )
                                }
                            }

                            if (editService > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(AppStrings.t("Biaya Layanan / Service:", "Service Fee:"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(CurrencyFormatter.formatRupiah(editService), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(AppStrings.t("Potongan Diskon:", "Discount:"), fontSize = 12.sp, color = IncomeGreen)
                                    IconButton(
                                        onClick = {
                                            editingDiscountText = if (editDiscount > 0) editDiscount.toLong().toString() else ""
                                            isDiscountDialogOpen = true
                                        },
                                        modifier = Modifier.size(24.dp).testTag("btn_edit_discount")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Diskon", modifier = Modifier.size(14.dp), tint = IncomeGreen)
                                    }
                                }
                                if (editDiscount > 0) {
                                    Text("- " + CurrencyFormatter.formatRupiah(editDiscount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = IncomeGreen)
                                } else {
                                    TextButton(
                                        onClick = {
                                            editingDiscountText = ""
                                            isDiscountDialogOpen = true
                                        }
                                    ) {
                                        Text(AppStrings.t("+ Tambah Diskon", "+ Add Discount"), fontSize = 11.sp, color = IncomeGreen)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = FinSmartPrimary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Total Terhitung:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = FinSmartPrimary
                                    )
                                    Text(
                                        text = CurrencyFormatter.formatRupiah(editTotalAmount),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = FinSmartPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = if (editTotalAmount > 0) editTotalAmount.toLong().toString() else "",
                                onValueChange = { input ->
                                    val cleaned = input.filter { it.isDigit() }
                                    editTotalAmount = cleaned.toDoubleOrNull() ?: 0.0
                                },
                                label = { Text(AppStrings.t("Total Akhir Transaksi", "Final Transaction Total")) },
                                placeholder = { Text("Rp 0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_edit_receipt_total"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Source Wallet Selection
                            Text(AppStrings.t("Pilih Sumber Dana (Dompet):", "Select Source Wallet:"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(wallets.size) { index ->
                                    val w = wallets[index]
                                    FilterChip(
                                        selected = selectedWallet?.id == w.id,
                                        onClick = { selectedWallet = w },
                                        label = { Text(w.name, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = FinSmartPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Action Buttons (Simpan & Split Bill AI)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Save to Transaction Button
                                Button(
                                    onClick = {
                                        if (!isUnlocked) {
                                            onTriggerPaywall()
                                            return@Button
                                        }
                                        if (editTotalAmount <= 0.0) {
                                            Toast.makeText(context, if (LocaleHelper.isEnglish()) "Total amount must be greater than 0." else "Total transaksi harus lebih dari Rp 0.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val wallet = selectedWallet ?: wallets.firstOrNull()
                                        if (wallet == null) {
                                            Toast.makeText(context, if (LocaleHelper.isEnglish()) "No wallet available. Please add a wallet in the Wallets menu first." else "Tidak ada dompet yang tersedia. Silakan tambahkan dompet di menu Dompet terlebih dahulu.", Toast.LENGTH_LONG).show()
                                            return@Button
                                        }

                                        val itemNotes = editItems.joinToString("\n") { "• ${it.qty}x ${it.name} (${CurrencyFormatter.formatRupiah(it.total)})" }
                                        val discountNote = if (editDiscount > 0) ", Diskon: ${CurrencyFormatter.formatRupiah(editDiscount)}" else ""
                                        val fullNote = "Nota: $editMerchantName\n$itemNotes\nSubtotal: ${CurrencyFormatter.formatRupiah(editSubtotal)}, Pajak: ${CurrencyFormatter.formatRupiah(editTax)}$discountNote"

                                        onSaveTransaction(
                                            editMerchantName.ifBlank { "Belanja Struk" },
                                            editTotalAmount,
                                            TransactionType.EXPENSE,
                                            editCategory,
                                            result.categoryIcon,
                                            wallet.id,
                                            wallet.name,
                                            null,
                                            null,
                                            System.currentTimeMillis(),
                                            fullNote,
                                            "#scan #nota #${editCategory.split(" ").first().lowercase()}",
                                            {
                                                Toast.makeText(context, if (LocaleHelper.isEnglish()) "Receipt transaction successfully saved!" else "Transaksi pengeluaran nota berhasil disimpan!", Toast.LENGTH_LONG).show()
                                                onNavigateBack()
                                            },
                                            { errMsg ->
                                                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("btn_save_scanned_receipt"),
                                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(AppStrings.t("Simpan Transaksi", "Save Transaction"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                // Split Bill AI Button
                                Button(
                                    onClick = {
                                        val updatedResult = result.copy(
                                            merchantName = editMerchantName,
                                            date = editDate,
                                            category = editCategory,
                                            subtotal = editSubtotal,
                                            tax = editTax,
                                            serviceCharge = editService,
                                            discount = editDiscount,
                                            totalAmount = editTotalAmount,
                                            taxTreatment = editTaxTreatment,
                                            items = editItems
                                        )
                                        onOpenSplitBill(updatedResult)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("btn_open_split_bill_from_receipt"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(AppStrings.t("Split Bill", "Split Bill"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Item Dialog
    if (isItemDialogOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { isItemDialogOpen = false },
            title = { Text(if (editingItemIndex >= 0) "Edit Item" else "Tambah Item Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editingItemName,
                        onValueChange = { editingItemName = it },
                        label = { Text(AppStrings.t("Nama Item / Makanan", "Item / Food Name")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editingItemQty,
                            onValueChange = { editingItemQty = it },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editingItemPrice,
                            onValueChange = { editingItemPrice = it },
                            label = { Text(AppStrings.t("Harga Satuan", "Unit Price")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(2f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = editingItemQty.toIntOrNull() ?: 1
                        val price = editingItemPrice.toDoubleOrNull() ?: 0.0
                        if (editingItemName.isNotBlank() && price > 0) {
                            val newItem = ReceiptItem(
                                id = UUID.randomUUID().toString(),
                                name = editingItemName,
                                qty = qty,
                                price = price,
                                total = price * qty
                            )
                            val list = editItems.toMutableList()
                            if (editingItemIndex >= 0 && editingItemIndex < list.size) {
                                list[editingItemIndex] = newItem
                            } else {
                                list.add(newItem)
                            }
                            editItems = list
                            recalculateTotals(list, editTax, editService, editDiscount, editTaxTreatment)
                            isItemDialogOpen = false
                        }
                    }
                ) {
                    Text(com.example.util.AppStrings.t("Simpan", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { isItemDialogOpen = false }) {
                    Text(com.example.util.AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }

    // Edit Discount Dialog
    if (isDiscountDialogOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { isDiscountDialogOpen = false },
            title = { Text(AppStrings.t("Edit Potongan Diskon / Voucher", "Edit Discount / Voucher"), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editingDiscountText,
                    onValueChange = { editingDiscountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(AppStrings.t("Nominal Diskon", "Discount Amount")) },
                    placeholder = { Text(com.example.util.AppStrings.t("Contoh: 10600", "e.g. 10600")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_discount")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = editingDiscountText.toDoubleOrNull() ?: 0.0
                        editDiscount = parsed
                        recalculateTotals(editItems, editTax, editService, parsed, editTaxTreatment)
                        isDiscountDialogOpen = false
                    }
                ) {
                    Text(AppStrings.t("Terapkan", "Apply"))
                }
            },
            dismissButton = {
                TextButton(onClick = { isDiscountDialogOpen = false }) {
                    Text(com.example.util.AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }
}
