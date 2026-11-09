package com.example.util

import com.example.data.model.DomainCurrency
import com.example.data.model.Money
import java.math.BigInteger

/**
 * Deterministic Hamilton-Hare (Largest Remainder) Allocation Engine.
 *
 * Guarantees:
 * 1. Exact Minor Unit Conservation: sum(allocations) == totalAmount.
 * 2. Arbitrary Precision: BigInteger intermediate multiplication prevents 64-bit overflow.
 * 3. Input-Order Independence: Sorts deterministically by (remainder DESC, canonicalId ASC).
 * 4. Strict Validation: Rejects negative weights and all-zero total weights.
 * 5. Sign-Safe: Supports positive, zero, and negative signed totals.
 */
object HamiltonHareAllocator {

    data class RecipientWeight(
        val id: String,
        val weight: BigInteger
    ) {
        constructor(id: String, weight: Long) : this(id, BigInteger.valueOf(weight))
    }

    data class AllocationResult(
        val id: String,
        val allocatedMinorUnits: Long
    )

    /**
     * Allocates a minor unit total among recipients using the Hamilton-Hare method.
     *
     * @param totalMinorUnits The total amount in minor units to allocate.
     * @param recipients The list of recipients with their respective weights.
     * @return Map of recipient ID to allocated minor units.
     */
    fun allocate(
        totalMinorUnits: Long,
        recipients: List<RecipientWeight>
    ): Map<String, Long> {
        require(recipients.isNotEmpty()) { "Recipients list cannot be empty" }

        // 1. Validate weights
        var totalWeight = BigInteger.ZERO
        for (r in recipients) {
            if (r.weight < BigInteger.ZERO) {
                throw IllegalArgumentException("Negative weight not allowed for recipient '${r.id}': ${r.weight}")
            }
            totalWeight += r.weight
        }
        if (totalWeight == BigInteger.ZERO) {
            throw IllegalArgumentException("Total weight of all recipients must be strictly positive")
        }

        if (totalMinorUnits == 0L) {
            return recipients.associate { it.id to 0L }
        }

        val sign = if (totalMinorUnits < 0L) -1L else 1L
        val absTotal = BigInteger.valueOf(kotlin.math.abs(totalMinorUnits))

        // 2. Base allocation and remainder calculation via BigInteger
        class Candidate(
            val id: String,
            var baseShare: BigInteger,
            val remainder: BigInteger
        )

        val candidates = recipients.map { r ->
            val numerator = absTotal.multiply(r.weight)
            val baseShare = numerator.divide(totalWeight)
            val remainder = numerator.remainder(totalWeight)
            Candidate(r.id, baseShare, remainder)
        }

        val totalBase = candidates.fold(BigInteger.ZERO) { acc, c -> acc + c.baseShare }
        val leftoverBig = absTotal.subtract(totalBase)
        val leftover = leftoverBig.toInt()

        // 3. Deterministic Sort: Remainder DESC, then Canonical ID ASC (order-independent)
        val sortedCandidates = candidates.sortedWith(
            compareByDescending<Candidate> { it.remainder }
                .thenBy { it.id }
        )

        // 4. Distribute leftover minor units to top candidates
        for (i in 0 until leftover) {
            sortedCandidates[i].baseShare = sortedCandidates[i].baseShare.add(BigInteger.ONE)
        }

        // 5. Build result map and verify exact conservation
        val resultMap = LinkedHashMap<String, Long>()
        var allocatedSum = 0L

        for (c in candidates) {
            val signedAmount = c.baseShare.longValueExact() * sign
            resultMap[c.id] = signedAmount
            allocatedSum += signedAmount
        }

        check(allocatedSum == totalMinorUnits) {
            "Conservation invariant violated: sum($allocatedSum) != total($totalMinorUnits)"
        }

        return resultMap
    }

    /**
     * Convenience method to allocate a Money object across recipients.
     */
    fun allocateMoney(
        total: Money,
        recipients: List<RecipientWeight>
    ): Map<String, Money> {
        val totalMinor = total.toMinorUnits()
        val allocationMap = allocate(totalMinor, recipients)
        return allocationMap.mapValues { (_, minor) ->
            Money.fromMinorUnits(minor, total.currency)
        }
    }
}
