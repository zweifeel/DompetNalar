package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.util.InstallmentDateHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarValidationTest {

    private lateinit var db: SQLiteDatabase
    private val dbName = "test_calendar_val.db"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
        db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        val cursor = db.rawQuery("SELECT sqlite_version()", null)
        if (cursor.moveToFirst()) {
            println("Robolectric SQLite Version: " + cursor.getString(0))
        }
        cursor.close()
        val c2 = db.rawQuery("SELECT date('2026-02-29'), date('2026-02-29') = '2026-02-29'", null)
        if (c2.moveToFirst()) {
            println("date('2026-02-29'): " + c2.getString(0) + ", equals: " + c2.getInt(1))
        }
        c2.close()
        db.execSQL("""
            CREATE TABLE test_dates (
                id INTEGER PRIMARY KEY,
                val TEXT CHECK(date(val) IS NOT NULL AND date(val) = val)
            )
        """.trimIndent())
    }

    @After
    fun tearDown() {
        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
    }

    private fun tryInsert(dateStr: String?): Boolean {
        return try {
            if (dateStr == null) {
                db.execSQL("INSERT INTO test_dates (val) VALUES (NULL)")
            } else {
                db.execSQL("INSERT INTO test_dates (val) VALUES (?)", arrayOf(dateStr))
            }
            println("Inserted successfully: $dateStr")
            true
        } catch (e: Exception) {
            println("Failed to insert $dateStr: ${e.message}")
            false
        }
    }

    @Test
    fun `P0-E ISO calendar date validation vectors`() {
        // Test strict Gregorian validation in domain helper across all 11 vectors
        // 1. Leap year 2024-02-29 -> VALID
        assertTrue(InstallmentDateHelper.isValidIsoDate("2024-02-29"))

        // 2. Non-leap year 2026-02-29 -> REJECTED
        assertFalse(InstallmentDateHelper.isValidIsoDate("2026-02-29"))

        // 3. 2026-02-30 -> REJECTED
        assertFalse(InstallmentDateHelper.isValidIsoDate("2026-02-30"))

        // 4. 2026-04-31 -> REJECTED (April has 30 days)
        assertFalse(InstallmentDateHelper.isValidIsoDate("2026-04-31"))

        // 5. 2026-04-30 -> VALID
        assertTrue(InstallmentDateHelper.isValidIsoDate("2026-04-30"))

        // 6. 2026-13-01 -> REJECTED (Month 13)
        assertFalse(InstallmentDateHelper.isValidIsoDate("2026-13-01"))

        // 7. 2026-00-10 -> REJECTED (Month 00)
        assertFalse(InstallmentDateHelper.isValidIsoDate("2026-00-10"))

        // 8. Non-date string -> REJECTED
        assertFalse(InstallmentDateHelper.isValidIsoDate("invalid-date"))

        // 9. Slash format 2026/04/30 -> REJECTED
        assertFalse(InstallmentDateHelper.isValidIsoDate("2026/04/30"))

        // 10. Non-padded format 2026-4-30 -> REJECTED
        assertFalse(InstallmentDateHelper.isValidIsoDate("2026-4-30"))

        // 11. NULL -> REJECTED
        assertFalse(InstallmentDateHelper.isValidIsoDate(null))

        // SQLite CHECK constraint testing:
        assertTrue(tryInsert("2024-02-29"))
        assertTrue(tryInsert("2026-04-30"))
        assertFalse(tryInsert("2026-13-01"))
        assertFalse(tryInsert("2026-00-10"))
        assertFalse(tryInsert("invalid-date"))
        assertFalse(tryInsert("2026/04/30"))
        assertFalse(tryInsert("2026-4-30"))
        assertFalse(tryInsert(null))
    }
}
