import os

# 1. Update WalletDraftManager.kt
mgr_path = r"app/src/main/java/com/example/data/chatbot/parser/WalletDraftManager.kt"
with open(mgr_path, "r", encoding="utf-8") as f:
    c_mgr = f.read().replace("\r\n", "\n")

old_mgr_func = """    suspend fun confirmAndSave(
        draftId: String,
        repository: FinanceRepository,
        userId: Long,
        accountMode: String = "PERSONAL",
        isEntitlementUnlocked: () -> Boolean = { true }
    ): Result<Wallet> = executionMutex.withLock {
        if (!isEntitlementUnlocked()) {
            return Result.failure(SecurityException("Akses ditolak: Fitur SmartMaster diperlukan untuk membuat dompet melalui AI."))
        }"""

new_mgr_func = """    suspend fun confirmAndSave(
        draftId: String,
        repository: FinanceRepository,
        userId: Long,
        accountMode: String = "PERSONAL",
        isEntitlementUnlocked: () -> Boolean
    ): Result<Wallet> = executionMutex.withLock {
        val isUnlocked = try {
            isEntitlementUnlocked()
        } catch (e: Exception) {
            false
        }
        if (!isUnlocked) {
            return Result.failure(SecurityException("Akses ditolak: Fitur SmartMaster diperlukan untuk membuat dompet melalui AI."))
        }
        if (userId <= 0L) {
            return Result.failure(SecurityException("Akses ditolak: Identitas pengguna tidak valid untuk eksekusi AI."))
        }"""

if old_mgr_func in c_mgr:
    c_mgr = c_mgr.replace(old_mgr_func, new_mgr_func)
    with open(mgr_path, "w", encoding="utf-8") as f:
        f.write(c_mgr)
    print("SUCCESS_MGR")
else:
    print("NOT_FOUND_MGR")

# 2. Update FinanceViewModel.kt
vm_path = r"app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt"
with open(vm_path, "r", encoding="utf-8") as f:
    c_vm = f.read().replace("\r\n", "\n")

old_vm_func = """    fun confirmWalletDraft(
        draftId: String,
        isEntitlementUnlocked: () -> Boolean = { false },
        onSuccess: (com.example.data.model.Wallet) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            if (!isEntitlementUnlocked()) {
                onError("Akses SmartMaster diperlukan untuk membuat dompet via AI.")
                return@launch
            }"""

new_vm_func = """    fun confirmWalletDraft(
        draftId: String,
        isEntitlementUnlocked: () -> Boolean,
        onSuccess: (com.example.data.model.Wallet) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val isUnlocked = try {
                isEntitlementUnlocked()
            } catch (e: Exception) {
                false
            }
            if (!isUnlocked) {
                onError("Akses SmartMaster diperlukan untuk membuat dompet via AI.")
                return@launch
            }"""

if old_vm_func in c_vm:
    c_vm = c_vm.replace(old_vm_func, new_vm_func)
    with open(vm_path, "w", encoding="utf-8") as f:
        f.write(c_vm)
    print("SUCCESS_VM")
else:
    print("NOT_FOUND_VM")

# 3. Update FinSmartApp.kt for SCAN_RECEIPT onSaveTransaction
app_path = r"app/src/main/java/com/example/ui/FinSmartApp.kt"
with open(app_path, "r", encoding="utf-8") as f:
    c_app = f.read().replace("\r\n", "\n")

old_scan_save = """                        onSaveTransaction = { title, amount, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, timestamp, note, tags, onSuccess, onError ->
                            financeViewModel.saveTransaction(
                                title = title,
                                amount = amount,
                                type = type,
                                category = category,
                                categoryIcon = categoryIcon,
                                walletId = walletId,
                                walletName = walletName,
                                targetWalletId = targetWalletId,
                                targetWalletName = targetWalletName,
                                timestamp = timestamp,
                                note = note,
                                tags = tags,
                                onSuccess = onSuccess,
                                onError = onError
                            )
                        }"""

new_scan_save = """                        onSaveTransaction = { title, amount, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, timestamp, note, tags, onSuccess, onError ->
                            if (!authViewModel.isFeatureUnlocked()) {
                                authViewModel.openPaywall()
                                onError(if (LocaleHelper.isEnglish()) "SmartMaster access required to record transaction via OCR." else "Akses SmartMaster diperlukan untuk menyimpan transaksi via OCR.")
                            } else {
                                financeViewModel.saveTransaction(
                                    title = title,
                                    amount = amount,
                                    type = type,
                                    category = category,
                                    categoryIcon = categoryIcon,
                                    walletId = walletId,
                                    walletName = walletName,
                                    targetWalletId = targetWalletId,
                                    targetWalletName = targetWalletName,
                                    timestamp = timestamp,
                                    note = note,
                                    tags = tags,
                                    onSuccess = onSuccess,
                                    onError = onError
                                )
                            }
                        }"""

if old_scan_save in c_app:
    c_app = c_app.replace(old_scan_save, new_scan_save)
    with open(app_path, "w", encoding="utf-8") as f:
        f.write(c_app)
    print("SUCCESS_APP_SCAN_SAVE")
else:
    print("NOT_FOUND_APP_SCAN_SAVE")
