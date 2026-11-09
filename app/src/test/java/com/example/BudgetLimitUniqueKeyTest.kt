package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.User
import com.example.data.model.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BudgetLimitUniqueKeyTest {

    private lateinit var db: FinSmartDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            db.userDao().insertUser(
                User(id = 1L, username = "user1", email = "u1@test.com", passwordHash = "h", role = UserRole.FREE_MEMBER, createdAt = 1L)
            )
            db.userDao().insertUser(
                User(id = 2L, username = "user2", email = "u2@test.com", passwordHash = "h", role = UserRole.FREE_MEMBER, createdAt = 2L)
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `T-02 BudgetLimitDao composite unique conflict updates existing row atomically`() = runBlocking {
        // Initial insert
        db.budgetLimitDao().upsertBudgetLimitByKey(
            categoryOrTotalId = "FOOD",
            limitAmount = 1000000.0,
            isCategory = true,
            userId = 1L
        )

        var limit = db.budgetLimitDao().getBudgetLimit(1L, "FOOD")
        assertNotNull(limit)
        assertEquals(1000000.0, limit?.limitAmount ?: 0.0, 0.001)
        assertEquals(1, db.budgetLimitDao().getBudgetLimitCount(1L))

        // Update with new amount on the same key
        db.budgetLimitDao().upsertBudgetLimitByKey(
            categoryOrTotalId = "FOOD",
            limitAmount = 1500000.0,
            isCategory = true,
            userId = 1L
        )

        limit = db.budgetLimitDao().getBudgetLimit(1L, "FOOD")
        assertNotNull(limit)
        assertEquals(1500000.0, limit?.limitAmount ?: 0.0, 0.001)
        assertEquals(1, db.budgetLimitDao().getBudgetLimitCount(1L)) // Count still exactly 1!

        // Multi-tenant check: user 2 can hold their own limit for "FOOD" independently
        db.budgetLimitDao().upsertBudgetLimitByKey(
            categoryOrTotalId = "FOOD",
            limitAmount = 500000.0,
            isCategory = true,
            userId = 2L
        )

        val u2Limit = db.budgetLimitDao().getBudgetLimit(2L, "FOOD")
        assertNotNull(u2Limit)
        assertEquals(500000.0, u2Limit?.limitAmount ?: 0.0, 0.001)
        assertEquals(1, db.budgetLimitDao().getBudgetLimitCount(2L))
    }
}
