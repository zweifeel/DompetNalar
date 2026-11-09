path_test = 'app/src/test/java/com/example/Phase1gUiLocalizationAndAccountRemovalTest.kt'
with open(path_test, 'r', encoding='utf-8') as f:
    text_test = f.read()

target = '''        // Verify badge string does not contain newline or vertical breaks
        assertFalse(badgeText.contains("\\n"))
        assertTrue(badgeText.length <= 15)
    }
}'''

replacement = '''        // Verify badge string does not contain newline or vertical breaks
        assertFalse(badgeText.contains("\\n"))
        assertTrue(badgeText.length <= 15)
    }

    // =========================================================================
    // 5. PHASE 1G.6: RESTORE LOCK SAFETY & PERMANENT RESTORATION GUARDS
    // =========================================================================

    @Test
    fun testReentrantPurgeUnderExclusiveMaintenance_completesCleanly() = runBlocking {
        // Direct proof that purge can be invoked reentrantly inside withExclusiveMaintenance
        val result = db.writeCoordinator.withExclusiveMaintenance {
            FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        }
        assertTrue("Reentrant purge under withExclusiveMaintenance must succeed", result.isSuccess)
    }

    @Test
    fun testRestoreUserDataInTransaction_purgesLukasUnderExclusiveMaintenanceWithoutDeadlock() = runBlocking {
        // Seed legitimate user
        val legitimateUserId = 888L
        val legUser = User(
            id = legitimateUserId,
            username = "ActiveUser",
            email = "active@example.com",
            role = UserRole.FREE_MEMBER
        )
        db.userDao().insertUser(legUser)

        // Seed target developer Lukas
        val lukasId = 999L
        val lukas = User(
            id = lukasId,
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV
        )
        db.userDao().insertUser(lukas)

        val payload = BackupDataPayload(
            version = 1,
            wallets = listOf(
                Wallet(id = 1L, name = "Active Wallet", type = WalletType.BANK, balance = 100000.0, userId = legitimateUserId)
            ),
            transactions = emptyList(),
            categories = emptyList(),
            budgetLimits = emptyList(),
            debts = emptyList(),
            assets = emptyList(),
            splitBillHistory = emptyList(),
            installmentPlans = emptyList(),
            recurringSchedules = emptyList()
        )

        // Execute restoreUserDataInTransaction which runs purge INSIDE withExclusiveMaintenance
        com.example.ui.viewmodel.FinanceViewModel.restoreUserDataInTransaction(
            database = db,
            payload = payload,
            targetUserId = legitimateUserId
        )

        // Verify Lukas is completely purged
        assertNull("Lukas user must be purged after restore", db.userDao().getUserById(lukasId))
        assertNull(db.userDao().getUserByEmail("lunachroc07@gmail.com"))
        assertNull(db.userDao().getUserByIdentifier("Lukas"))

        // Verify ActiveUser and restored wallet are present and untouched
        val restoredUser = db.userDao().getUserById(legitimateUserId)
        assertNotNull(restoredUser)
        assertEquals("ActiveUser", restoredUser?.username)
        val wallets = db.walletDao().getAllWallets(legitimateUserId).first()
        assertEquals(1, wallets.size)
        assertEquals("Active Wallet", wallets.first().name)
    }

    @Test
    fun testSessionManagerRestoreSession_blocksLukasEvenIfPresentInDatabase() = runBlocking {
        val lukasId = 991L
        val lukas = User(
            id = lukasId,
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV
        )
        db.userDao().insertUser(lukas)

        // Set saved session ID to Lukas
        val prefs = context.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("saved_user_id", lukasId).apply()

        // Even in DEBUG mode (isDebug = true), SessionManager MUST reject restoring Lukas
        val restored = SessionManager.restoreSession(db.userDao(), isDebug = true)
        assertFalse("SessionManager must NEVER restore Lukas session", restored)
        assertEquals("Saved user ID must be wiped", -1L, prefs.getLong("saved_user_id", -1L))
        assertNull("Current session user must be null", SessionManager.currentUser.value)
    }
}'''

text_test = text_test.replace('\r\n', '\n')
target = target.replace('\r\n', '\n')
replacement = replacement.replace('\r\n', '\n')

assert target in text_test, 'target not found'
new_text = text_test.replace(target, replacement, 1)

with open(path_test, 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Added Phase 1G.6 targeted tests to Phase1gUiLocalizationAndAccountRemovalTest.kt')
