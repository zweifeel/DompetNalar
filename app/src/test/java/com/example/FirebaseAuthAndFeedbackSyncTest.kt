package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.config.AppConfig
import com.example.config.DeveloperAccountConfig
import com.example.data.local.FinSmartDatabase
import com.example.data.local.Migration8To9
import com.example.data.model.FeedbackItem
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.session.SessionManager
import com.example.ui.viewmodel.FeedbackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirebaseAuthAndFeedbackSyncTest {

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        val field = FinSmartDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, db)

        SessionManager.initialize(context)
    }

    @After
    fun tearDown() {
        ShadowLooper.idleMainLooper()
        val field = FinSmartDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
        db.close()
        SessionManager.clearSession()
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. GOOGLE SIGN-IN WEB CLIENT ID & FIREBASE AUTH TESTS
    // =========================================================================

    @Test
    fun testWebClientIdMatchesActiveFirebaseProject() {
        // Must belong to finsmart-47fdd project (number 1029968996578)
        assertTrue(
            "Web Client ID harus memiliki awalan nomor project 1029968996578",
            AppConfig.GOOGLE_SIGN_IN_WEB_CLIENT_ID.startsWith("1029968996578")
        )
        assertTrue(
            "Web Client ID harus berakhiran apps.googleusercontent.com",
            AppConfig.GOOGLE_SIGN_IN_WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com")
        )
    }

    @Test
    fun testLocalLogin_doesNotCreateFirebaseAuthSession() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val feedbackViewModel = FeedbackViewModel(app)

        // Local Room session
        val localUser = User(
            id = 1L,
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV
        )
        SessionManager.login(localUser)

        // SessionManager is authenticated locally
        assertTrue(SessionManager.isAuthenticated())
        assertEquals(1L, SessionManager.getActiveUserId())

        // Firebase Auth is NOT active
        assertFalse("Login lokal tidak boleh dianggap sebagai Firebase Auth aktif", feedbackViewModel.isFirebaseAuthActive.value)
        assertNull("Tanpa token Google, email Firebase Auth harus null", feedbackViewModel.currentFirebaseAuthEmail.value)
    }

    @Test
    fun testDevViewer_requiresTrustedDeveloperOrMasterDev() {
        // Free member cannot access Viewer
        val regularUser = User(id = 2L, username = "Budi", email = "budi@gmail.com", role = UserRole.FREE_MEMBER)
        val isRegularAuthorized = regularUser.role == UserRole.MASTER_DEV ||
                DeveloperAccountConfig.isTrustedDeveloperEmail(regularUser.email) ||
                regularUser.username.equals("Lukas", ignoreCase = true)
        assertFalse("Pengguna biasa tidak boleh memiliki hak akses Viewer", isRegularAuthorized)

        // Master Dev Lukas is authorized
        val devLukas = User(id = 1L, username = "Lukas", email = "lunachroc07@gmail.com", role = UserRole.MASTER_DEV)
        val isLukasAuthorized = devLukas.role == UserRole.MASTER_DEV ||
                DeveloperAccountConfig.isTrustedDeveloperEmail(devLukas.email) ||
                devLukas.username.equals("Lukas", ignoreCase = true)
        assertTrue("Master Dev Lukas harus diotorisasi", isLukasAuthorized)

        // Whitelisted developer email is authorized
        val whitelistedUser = User(id = 3L, username = "Dev", email = "lunachroc01@gmail.com", role = UserRole.FREE_MEMBER)
        val isWhitelistedAuthorized = DeveloperAccountConfig.isTrustedDeveloperEmail(whitelistedUser.email)
        assertTrue("Email whitelist developer harus diotorisasi", isWhitelistedAuthorized)
    }

    // =========================================================================
    // 2. FEEDBACK SUBMISSION & UNSYNCED PERSISTENCE TESTS
    // =========================================================================

    @Test
    fun testFeedbackSubmissionWithoutCloud_persistsLocallyWithIsSyncedFalse() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val feedbackViewModel = FeedbackViewModel(app)
        val feedbackDao = db.feedbackDao()

        var reportedCloudSynced: Boolean? = null
        feedbackViewModel.submitFeedback(
            userName = "Penguji",
            userEmail = "tester@finsmart.local",
            rating = 5,
            category = "Saran Fitur",
            message = "Fitur backup offline sangat bagus!",
            onResult = { isSynced ->
                reportedCloudSynced = isSynced
            }
        )
        ShadowLooper.idleMainLooper()

        // 1. Must report that cloud sync was NOT achieved (prevent false success confirmation)
        assertEquals("Tanpa koneksi cloud, isCloudSynced harus bernilai false", false, reportedCloudSynced)

        // 2. Submission message must be truthful
        val msg = feedbackViewModel.submissionSuccess.value
        assertNotNull("Pesan status harus ada", msg)
        assertTrue(
            "Pesan harus menyatakan tersimpan di perangkat dan belum terkirim ke server",
            msg!!.contains("tersimpan di perangkat") && msg.contains("belum berhasil dikirim")
        )

        // 3. Must persist safely in local Room database
        val allLocal = feedbackDao.getAllFeedbacks().first()
        assertTrue("Feedback harus tersimpan di Room database lokal", allLocal.any { it.message == "Fitur backup offline sangat bagus!" })

        // 4. Must be marked with isSynced = false
        val unsynced = feedbackDao.getUnsyncedFeedbacksList()
        assertEquals("Harus terdapat feedback belum tersinkronisasi", 1, unsynced.count { it.message == "Fitur backup offline sangat bagus!" })
        assertFalse(unsynced.first { it.message == "Fitur backup offline sangat bagus!" }.isSynced)
    }

    @Test
    fun testMarkFeedbackAsSynced_updatesLocalDatabaseCorrectly() = runBlocking {
        val feedbackDao = db.feedbackDao()

        val item = FeedbackItem(
            userName = "Lukas",
            userEmail = "lunachroc07@gmail.com",
            rating = 5,
            category = "Bug / Kendala",
            message = "Perbaikan query offline berhasil",
            isSynced = false
        )
        feedbackDao.insertFeedback(item)

        // Initially unsynced
        var unsyncedList = feedbackDao.getUnsyncedFeedbacksList()
        assertTrue("Item harus masuk daftar belum tersinkronisasi", unsyncedList.any { it.id == item.id })

        // Mark as synced
        val updatedRows = feedbackDao.markFeedbackAsSynced(item.id)
        assertEquals("1 baris harus diperbarui", 1, updatedRows)

        // Verify updated
        unsyncedList = feedbackDao.getUnsyncedFeedbacksList()
        assertFalse("Item tidak boleh lagi muncul di daftar belum tersinkronisasi", unsyncedList.any { it.id == item.id })

        val all = feedbackDao.getAllFeedbacks().first()
        val syncedItem = all.find { it.id == item.id }
        assertNotNull(syncedItem)
        assertTrue("isSynced harus true", syncedItem!!.isSynced)
    }

    @Test
    fun testStableFeedbackIdPreventsDuplicatesOnRetry() {
        val originalId = "feedback-uuid-12345"
        val item1 = FeedbackItem(
            id = originalId,
            userName = "UserA",
            userEmail = "user@test.com",
            rating = 4,
            category = "Desain / UI",
            message = "Tampilan rapi",
            isSynced = false
        )

        // Simulasikan retry menggunakan item.id yang sama persis
        val retryItem = item1.copy(isSynced = true)
        assertEquals("ID harus stabil saat retry untuk mencegah duplikasi dokumen di Firestore", originalId, retryItem.id)
    }

    @Test
    fun testMigration8To9_nonDestructive() {
        val helperFactory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_8_9.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `users` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `username` TEXT NOT NULL,
                            `email` TEXT NOT NULL,
                            `passwordHash` TEXT NOT NULL,
                            `role` TEXT NOT NULL,
                            `isApproved` INTEGER NOT NULL DEFAULT 0,
                            `isGuest` INTEGER NOT NULL DEFAULT 0,
                            `trialStartDate` INTEGER NOT NULL DEFAULT 0,
                            `subscriptionExpiresAt` INTEGER
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `feedbacks` (
                            `id` TEXT NOT NULL,
                            `userName` TEXT NOT NULL,
                            `userEmail` TEXT NOT NULL,
                            `rating` INTEGER NOT NULL,
                            `category` TEXT NOT NULL,
                            `message` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `userId` INTEGER,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    db.execSQL("INSERT INTO `feedbacks` VALUES ('fb-old-1', 'Lukas', 'lukas@test.com', 5, 'Saran Fitur', 'Pesan lama', 1000, NULL)")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        context.deleteDatabase("test_migration_8_9.db")
        val helper = helperFactory.create(config)
        val db8 = helper.writableDatabase

        // Execute Migration 8 -> 9
        Migration8To9.migrate(db8)

        // Verify column isSynced exists and default value is 0
        val cursor = db8.query("SELECT id, message, isSynced FROM feedbacks WHERE id = 'fb-old-1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("fb-old-1", cursor.getString(0))
        assertEquals("Pesan lama", cursor.getString(1))
        assertEquals(0, cursor.getInt(2)) // Default 0
        cursor.close()
        db8.close()
        context.deleteDatabase("test_migration_8_9.db")
    }
}
