package com.example

import com.example.util.HamiltonHareAllocator
import com.example.util.HamiltonHareAllocator.RecipientWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class HamiltonHareAllocatorTest {

    @Test
    fun `1 Equal weights distribution`() {
        // 100 minor units split among 3 equal weights
        val recipients = listOf(
            RecipientWeight("alice", 1),
            RecipientWeight("bob", 1),
            RecipientWeight("charlie", 1)
        )
        val result = HamiltonHareAllocator.allocate(100L, recipients)

        // 100 / 3 = 33 r 1 -> 1 extra unit goes to "alice" (lexicographically smallest ID among ties)
        assertEquals(34L, result["alice"])
        assertEquals(33L, result["bob"])
        assertEquals(33L, result["charlie"])
        assertEquals(100L, result.values.sum())
    }

    @Test
    fun `2 Unequal weights proportional distribution`() {
        // 1000 minor units split 50%, 30%, 20%
        val recipients = listOf(
            RecipientWeight("a", 50),
            RecipientWeight("b", 30),
            RecipientWeight("c", 20)
        )
        val result = HamiltonHareAllocator.allocate(1000L, recipients)
        assertEquals(500L, result["a"])
        assertEquals(300L, result["b"])
        assertEquals(200L, result["c"])
        assertEquals(1000L, result.values.sum())
    }

    @Test
    fun `3 Fractional remainder ties with canonical ID tie breaker`() {
        // 10 units among 4 recipients with equal weights: 2 base each, leftover 2
        // Tied remainders: 10 % 4 = 2. Canonical IDs "user_A", "user_B", "user_C", "user_D"
        val recipients = listOf(
            RecipientWeight("user_C", 1),
            RecipientWeight("user_A", 1),
            RecipientWeight("user_D", 1),
            RecipientWeight("user_B", 1)
        )
        val result = HamiltonHareAllocator.allocate(10L, recipients)

        // user_A and user_B should get 3 each, user_C and user_D should get 2 each
        assertEquals(3L, result["user_A"])
        assertEquals(3L, result["user_B"])
        assertEquals(2L, result["user_C"])
        assertEquals(2L, result["user_D"])
        assertEquals(10L, result.values.sum())
    }

    @Test
    fun `4 Different input orderings produce identical allocations`() {
        val total = 105L
        val list1 = listOf(
            RecipientWeight("r3", 10),
            RecipientWeight("r1", 20),
            RecipientWeight("r2", 30)
        )
        val list2 = listOf(
            RecipientWeight("r1", 20),
            RecipientWeight("r2", 30),
            RecipientWeight("r3", 10)
        )
        val list3 = listOf(
            RecipientWeight("r2", 30),
            RecipientWeight("r3", 10),
            RecipientWeight("r1", 20)
        )

        val res1 = HamiltonHareAllocator.allocate(total, list1)
        val res2 = HamiltonHareAllocator.allocate(total, list2)
        val res3 = HamiltonHareAllocator.allocate(total, list3)

        assertEquals(res1, res2)
        assertEquals(res2, res3)
    }

    @Test
    fun `5 Stable canonical ID tie-breaking`() {
        // 1 unit to distribute between two identical remainder candidates
        val recipients = listOf(
            RecipientWeight("zeta", 1),
            RecipientWeight("alpha", 1)
        )
        val result = HamiltonHareAllocator.allocate(1L, recipients)
        // alpha wins tie-break because "alpha" < "zeta"
        assertEquals(1L, result["alpha"])
        assertEquals(0L, result["zeta"])
    }

    @Test
    fun `6 Very large amounts handled without 64-bit integer overflow`() {
        // Long.MAX_VALUE - 1000 with very large weights
        val largeAmount = 900_000_000_000_000L // 900 Trillion minor units
        val recipients = listOf(
            RecipientWeight("rec1", BigInteger("1000000000000000000")),
            RecipientWeight("rec2", BigInteger("2000000000000000000"))
        )
        val result = HamiltonHareAllocator.allocate(largeAmount, recipients)
        assertEquals(300_000_000_000_000L, result["rec1"])
        assertEquals(600_000_000_000_000L, result["rec2"])
        assertEquals(largeAmount, result.values.sum())
    }

    @Test
    fun `7 Zero weights allocate zero without error`() {
        val recipients = listOf(
            RecipientWeight("active", 10),
            RecipientWeight("inactive", 0)
        )
        val result = HamiltonHareAllocator.allocate(500L, recipients)
        assertEquals(500L, result["active"])
        assertEquals(0L, result["inactive"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `8 Negative weights are rejected`() {
        val recipients = listOf(
            RecipientWeight("a", 10),
            RecipientWeight("b", -5)
        )
        HamiltonHareAllocator.allocate(100L, recipients)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `9 All-zero weights are rejected`() {
        val recipients = listOf(
            RecipientWeight("a", 0),
            RecipientWeight("b", 0)
        )
        HamiltonHareAllocator.allocate(100L, recipients)
    }

    @Test
    fun `10 Exact total conservation across various odd amounts`() {
        val recipients = listOf(
            RecipientWeight("u1", 7),
            RecipientWeight("u2", 11),
            RecipientWeight("u3", 13),
            RecipientWeight("u4", 17)
        )
        val testTotals = listOf(1L, 2L, 3L, 47L, 9999L, 1234567L, 888888888L)
        for (total in testTotals) {
            val result = HamiltonHareAllocator.allocate(total, recipients)
            assertEquals(total, result.values.sum())
        }
    }
}
