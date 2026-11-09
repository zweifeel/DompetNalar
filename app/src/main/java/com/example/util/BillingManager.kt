package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Google Play Billing Manager (Play Billing Library v8.0.0).
 *
 * Mengelola integrasi resmi Google Play Billing Client:
 * 1. Lifecycle koneksi BillingClient dengan auto-reconnect.
 * 2. Query ProductDetails untuk In-App & Subscription dengan harga lokal dinamis.
 * 3. Peluncuran alur pembelian resmi (launchBillingFlow).
 * 4. Pemrosesan transaksi, validasi purchaseToken, dan acknowledgement wajib sesuai regulasi Google Play.
 * 5. Query dan pemulihan pembelian aktif (restorePurchases).
 * 6. Pemetaan hasil transaksi ke EntitlementManager dan AuthViewModel.
 */
object BillingManager : PurchasesUpdatedListener {

    private const val TAG = "BillingManager"

    // Product IDs resmi FinSmart
    const val PRODUCT_ID_MONTHLY = "smart_master_monthly"
    const val PRODUCT_ID_YEARLY = "smart_master_yearly"
    const val PRODUCT_ID_LIFETIME = "smart_master_lifetime"

    enum class BillingConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        UNAVAILABLE
    }

    sealed class PurchaseEvent {
        data class Success(val productId: String, val purchase: Purchase) : PurchaseEvent()
        data class Pending(val productId: String) : PurchaseEvent()
        object UserCanceled : PurchaseEvent()
        data class Error(val code: Int, val message: String) : PurchaseEvent()
    }

    private var billingClient: BillingClient? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // State Flows
    private val _connectionStatus = MutableStateFlow(BillingConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<BillingConnectionStatus> = _connectionStatus.asStateFlow()

    private val _productDetailsMap = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetailsMap: StateFlow<Map<String, ProductDetails>> = _productDetailsMap.asStateFlow()

    private val _monthlyPrice = MutableStateFlow<String?>(null)
    val monthlyPrice: StateFlow<String?> = _monthlyPrice.asStateFlow()

    private val _yearlyPrice = MutableStateFlow<String?>(null)
    val yearlyPrice: StateFlow<String?> = _yearlyPrice.asStateFlow()

    private val _lifetimePrice = MutableStateFlow<String?>(null)
    val lifetimePrice: StateFlow<String?> = _lifetimePrice.asStateFlow()

    private val _purchaseEvents = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 1)
    val purchaseEvents: SharedFlow<PurchaseEvent> = _purchaseEvents.asSharedFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private const val PREFS_BILLING = "finsmart_billing_records"
    private const val KEY_PROCESSED_TOKENS = "processed_purchase_tokens"
    private val processedTokens = mutableSetOf<String>()
    private var appContext: Context? = null

    // Callback saat entitlement pembelian berhasil divalidasi dan di-acknowledge
    private var onPurchaseSuccessCallback: ((productId: String, purchaseToken: String, orderId: String?, purchaseTime: Long, isAutoRenewing: Boolean, isExplicitPurchase: Boolean) -> Unit)? = null

    // Callback sinkronisasi status seluruh subscription aktif (dengan status konektivitas terverifikasi)
    var onSubscriptionsSynced: ((activeSubscriptions: List<Purchase>, isConnected: Boolean) -> Unit)? = null

    /**
     * Memeriksa apakah token pembelian telah diproses sebelumnya untuk mencegah efek samping ganda.
     */
    fun isTokenProcessed(token: String): Boolean {
        return processedTokens.contains(token)
    }

    /**
     * Menandai token pembelian sebagai telah diproses secara persisten.
     */
    fun markTokenProcessed(token: String) {
        if (token.isBlank()) return
        processedTokens.add(token)
        try {
            appContext?.getSharedPreferences(PREFS_BILLING, Context.MODE_PRIVATE)
                ?.edit()
                ?.putStringSet(KEY_PROCESSED_TOKENS, HashSet(processedTokens))
                ?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menyimpan processedTokens: ${e.message}")
        }
    }

    /**
     * Menghapus cache token yang telah diproses (untuk pengujian atau reset sesi).
     */
    fun clearProcessedTokens() {
        processedTokens.clear()
        try {
            appContext?.getSharedPreferences(PREFS_BILLING, Context.MODE_PRIVATE)
                ?.edit()
                ?.remove(KEY_PROCESSED_TOKENS)
                ?.apply()
        } catch (_: Exception) {}
    }

    /**
     * Melindungi purchaseToken agar tidak pernah dicetak utuh dalam log.
     */
    fun maskToken(token: String?): String {
        if (token.isNullOrBlank()) return "[EMPTY]"
        return if (token.length <= 10) "***" else "${token.take(4)}...${token.takeLast(4)}"
    }

    /**
     * Inisialisasi BillingClient dengan dukungan isExplicitPurchase flag.
     */
    fun initialize(
        context: Context,
        onPurchaseSuccess: (productId: String, purchaseToken: String, orderId: String?, purchaseTime: Long, isAutoRenewing: Boolean, isExplicitPurchase: Boolean) -> Unit
    ) {
        this.appContext = context.applicationContext
        this.onPurchaseSuccessCallback = onPurchaseSuccess

        // Muat riwayat token yang telah diproses
        try {
            val saved = appContext?.getSharedPreferences(PREFS_BILLING, Context.MODE_PRIVATE)
                ?.getStringSet(KEY_PROCESSED_TOKENS, emptySet())
            if (saved != null) {
                processedTokens.addAll(saved)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal memuat processedTokens: ${e.message}")
        }

        if (billingClient != null && billingClient?.isReady == true) {
            Log.d(TAG, "BillingClient sudah terinisialisasi dan siap.")
            queryProductDetails()
            return
        }

        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()

        startConnection()
    }

    /**
     * Overload kompatibilitas untuk pemanggil dengan 5 parameter callback.
     */
    fun initialize(
        context: Context,
        onPurchaseSuccess: (productId: String, purchaseToken: String, orderId: String?, purchaseTime: Long, isAutoRenewing: Boolean) -> Unit
    ) {
        initialize(context) { productId, purchaseToken, orderId, purchaseTime, isAutoRenewing, _ ->
            onPurchaseSuccess(productId, purchaseToken, orderId, purchaseTime, isAutoRenewing)
        }
    }

    private fun startConnection() {
        _connectionStatus.value = BillingConnectionStatus.CONNECTING
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Koneksi Google Play Billing berhasil terbentuk.")
                    _connectionStatus.value = BillingConnectionStatus.CONNECTED
                    queryProductDetails()
                    queryActivePurchases()
                } else {
                    Log.w(TAG, "Gagal menghubungkan ke Google Play Billing: ${billingResult.debugMessage} (code: ${billingResult.responseCode})")
                    _connectionStatus.value = BillingConnectionStatus.UNAVAILABLE
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Koneksi Google Play Billing terputus. Mencoba menghubungkan kembali saat dibutuhkan.")
                _connectionStatus.value = BillingConnectionStatus.DISCONNECTED
            }
        })
    }

    /**
     * Mengambil detail produk resmi (harga lokal & offer tokens) dari Google Play.
     */
    fun queryProductDetails() {
        val client = billingClient ?: return
        if (!client.isReady) {
            Log.w(TAG, "BillingClient belum siap saat queryProductDetails dipanggil.")
            return
        }

        scope.launch {
            // 1. Subscription Products (Monthly, Yearly)
            val subProductList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID_MONTHLY)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID_YEARLY)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
            val subParams = QueryProductDetailsParams.newBuilder()
                .setProductList(subProductList)
                .build()

            val combinedMap = mutableMapOf<String, ProductDetails>()

            // Query Active Subscription Products only (Monthly & Yearly)
            client.queryProductDetailsAsync(subParams) { subResult, queryProductDetailsResult ->
                if (subResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val subDetailsList = queryProductDetailsResult.productDetailsList
                    subDetailsList.forEach { details ->
                        combinedMap[details.productId] = details
                        val formattedPrice = details.subscriptionOfferDetails?.firstOrNull()
                            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                        if (details.productId == PRODUCT_ID_MONTHLY) {
                            _monthlyPrice.value = formattedPrice
                        } else if (details.productId == PRODUCT_ID_YEARLY) {
                            _yearlyPrice.value = formattedPrice
                        }
                    }
                } else {
                    Log.w(TAG, "Gagal query detail subscription: ${subResult.debugMessage}")
                }
                _productDetailsMap.value = combinedMap
                Log.d(TAG, "Detail produk langganan aktif: ${combinedMap.keys}")
            }
        }
    }

    /**
     * Meluncurkan antarmuka pembelian resmi Google Play.
     */
    fun launchPurchaseFlow(activity: Activity, productId: String): BillingResult {
        if (productId == PRODUCT_ID_LIFETIME) {
            Log.w(TAG, "Opsi pembelian paket seumur hidup (lifetime) telah dinonaktifkan sesuai kebijakan produk.")
            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR)
                .setDebugMessage("Paket seumur hidup tidak lagi tersedia untuk pembelian baru.")
                .build()
        }

        val client = billingClient
        if (client == null || !client.isReady) {
            Log.e(TAG, "BillingClient belum siap untuk meluncurkan pembelian.")
            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                .setDebugMessage("Layanan Google Play Billing belum terhubung.")
                .build()
        }

        val productDetails = _productDetailsMap.value[productId]
        if (productDetails == null) {
            val connectionState = _connectionStatus.value
            val isConnected = client.isReady && connectionState == BillingConnectionStatus.CONNECTED
            val diagnosticMsg = if (!isConnected) {
                "Layanan Google Play Billing belum terhubung. Pastikan koneksi internet stabil dan Play Store aktif."
            } else if (_productDetailsMap.value.isEmpty()) {
                "Detail langganan '$productId' belum aktif di Google Play. Pastikan produk Subscription dan Base Plan aktif di Play Console serta akun tester terdaftar."
            } else {
                "Produk '$productId' tidak terdaftar dalam katalog langganan aktif."
            }
            Log.e(TAG, "ProductDetails tidak ditemukan untuk ID: $productId: $diagnosticMsg")
            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
                .setDebugMessage(diagnosticMsg)
                .build()
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (productDetails.productType == BillingClient.ProductType.SUBS) {
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken != null) {
                productDetailsParamsBuilder.setOfferToken(offerToken)
            } else {
                Log.w(TAG, "Offer token tidak ditemukan untuk subscription: $productId")
                return BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR)
                    .setDebugMessage("Base Plan/Offer untuk paket '$productId' belum aktif di Google Play Console untuk negara/akun Anda.")
                    .build()
            }
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        _isProcessing.value = true
        return client.launchBillingFlow(activity, billingFlowParams)
    }

    /**
     * Callback dari Google Play saat transaksi selesai / diperbarui.
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        _isProcessing.value = false
        val responseCode = billingResult.responseCode

        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    purchases.forEach { purchase ->
                        handlePurchase(purchase, isExplicitPurchase = true)
                    }
                } else {
                    Log.w(TAG, "onPurchasesUpdated OK tetapi daftar pembelian kosong.")
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "Pembelian dibatalkan oleh pengguna.")
                scope.launch { _purchaseEvents.emit(PurchaseEvent.UserCanceled) }
            }
            else -> {
                Log.e(TAG, "Pembelian gagal: ${billingResult.debugMessage} (code: $responseCode)")
                scope.launch {
                    _purchaseEvents.emit(PurchaseEvent.Error(responseCode, billingResult.debugMessage))
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase, isExplicitPurchase: Boolean = false) {
        val productId = purchase.products.firstOrNull() ?: return
        val maskedToken = maskToken(purchase.purchaseToken)

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                Log.i(TAG, "Transaksi diverifikasi: $productId (Order: ${purchase.orderId}, Token: $maskedToken, isExplicit=$isExplicitPurchase)")

                // Google Play mewajibkan acknowledgement
                if (!purchase.isAcknowledged) {
                    val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()

                    billingClient?.acknowledgePurchase(acknowledgeParams) { ackResult ->
                        if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.i(TAG, "Pembelian $productId berhasil di-acknowledge.")
                            markTokenProcessed(purchase.purchaseToken)
                            grantEntitlement(productId, purchase, isExplicitPurchase)
                        } else {
                            Log.e(TAG, "Gagal acknowledge pembelian $productId: ${ackResult.debugMessage} (code: ${ackResult.responseCode})")
                        }
                    }
                } else {
                    markTokenProcessed(purchase.purchaseToken)
                    grantEntitlement(productId, purchase, isExplicitPurchase)
                }
            }
            Purchase.PurchaseState.PENDING -> {
                Log.w(TAG, "Pembelian $productId masih dalam status PENDING (menunggu pembayaran). Entitlement tidak diberikan.")
                scope.launch { _purchaseEvents.emit(PurchaseEvent.Pending(productId)) }
            }
            Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                Log.w(TAG, "Status pembelian $productId tidak spesifik.")
            }
        }
    }

    private fun grantEntitlement(productId: String, purchase: Purchase, isExplicitPurchase: Boolean) {
        scope.launch {
            if (isExplicitPurchase) {
                _purchaseEvents.emit(PurchaseEvent.Success(productId, purchase))
            }
            withContext(Dispatchers.Main) {
                onPurchaseSuccessCallback?.invoke(
                    productId,
                    purchase.purchaseToken,
                    purchase.orderId,
                    purchase.purchaseTime,
                    purchase.isAutoRenewing,
                    isExplicitPurchase
                )
            }
        }
    }

    /**
     * Memeriksa dan memulihkan pembelian aktif yang masih sah (Restore Purchases).
     * @param isSilent jika true, tidak memancarkan event pembelian baru atau notifikasi popup berulang.
     * @param onComplete callback hasil restore pembelian.
     */
    fun restorePurchases(
        isSilent: Boolean = false,
        onComplete: ((successCount: Int, message: String) -> Unit)? = null
    ) {
        val client = billingClient
        if (client == null || !client.isReady) {
            onSubscriptionsSynced?.invoke(emptyList(), false)
            scope.launch(Dispatchers.Main) {
                onComplete?.invoke(0, "Layanan Google Play Billing belum siap.")
            }
            return
        }

        _isProcessing.value = true

        scope.launch {
            var restoredCount = 0

            // 1. Query Subscription aktif
            val subParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            client.queryPurchasesAsync(subParams) { subResult, subPurchases ->
                val isSubOk = subResult.responseCode == BillingClient.BillingResponseCode.OK
                val activeSubs = if (isSubOk) {
                    subPurchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                } else {
                    emptyList()
                }

                activeSubs.forEach { purchase ->
                    handlePurchase(purchase, isExplicitPurchase = !isSilent)
                    restoredCount++
                }

                // Kirim hasil sinkronisasi subscription aktif beserta konfirmasi konektivitas
                onSubscriptionsSynced?.invoke(activeSubs, isSubOk)

                // 2. Query In-App aktif (Lifetime)
                val inAppParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()

                client.queryPurchasesAsync(inAppParams) { inAppResult, inAppPurchases ->
                    if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        inAppPurchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
                            handlePurchase(purchase, isExplicitPurchase = !isSilent)
                            restoredCount++
                        }
                    }

                    scope.launch(Dispatchers.Main) {
                        _isProcessing.value = false
                        val msg = if (restoredCount > 0) {
                            "Berhasil memulihkan $restoredCount langganan aktif Google Play!"
                        } else {
                            "Tidak ditemukan langganan aktif yang dapat dipulihkan."
                        }
                        onComplete?.invoke(restoredCount, msg)
                    }
                }
            }
        }
    }

    /**
     * Query status pembelian saat aplikasi dimulai untuk sinkronisasi otomatis (Silent Sync).
     */
    private fun queryActivePurchases() {
        restorePurchases(isSilent = true) { count, _ ->
            if (count > 0) {
                Log.i(TAG, "Otomatis menyinkronkan $count pembelian aktif pada start.")
            }
        }
    }

    /**
     * Helper untuk mendapatkan teks harga resmi terformat.
     */
    fun getFormattedPrice(productId: String, fallbackPrice: String): String {
        return when (productId) {
            PRODUCT_ID_MONTHLY -> _monthlyPrice.value ?: fallbackPrice
            PRODUCT_ID_YEARLY -> _yearlyPrice.value ?: fallbackPrice
            PRODUCT_ID_LIFETIME -> _lifetimePrice.value ?: fallbackPrice
            else -> fallbackPrice
        }
    }

    /**
     * Membersihkan resource saat aplikasi dihancurkan.
     */
    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
        _connectionStatus.value = BillingConnectionStatus.DISCONNECTED
    }
}
